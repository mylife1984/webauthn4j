/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webauthn4j.metadata.validator.attestation.trustworthiness.certpath;

import com.webauthn4j.data.AuthenticatorAttestationType;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import com.webauthn4j.data.attestation.statement.FIDOU2FAttestationStatement;
import com.webauthn4j.metadata.MetadataBLOBProvider;
import com.webauthn4j.metadata.data.MetadataBLOBPayloadEntry;
import com.webauthn4j.metadata.data.toc.StatusReport;
import com.webauthn4j.metadata.exception.BadStatusException;
import com.webauthn4j.util.HexUtil;
import com.webauthn4j.validator.attestation.trustworthiness.certpath.CertPathTrustworthinessValidatorBase;
import com.webauthn4j.validator.exception.BadAttestationStatementException;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.security.cert.TrustAnchor;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MetadataBLOBBasedCertPathTrustworthinessValidator extends CertPathTrustworthinessValidatorBase {

    private static final String SUBJECT_KEY_IDENTIFIER_OID = "2.5.29.14";

    private final List<MetadataBLOBProvider> metadataBLOBProviders;

    private boolean notFidoCertifiedAllowed = false;
    private boolean selfAssertionSubmittedAllowed = false;

    public MetadataBLOBBasedCertPathTrustworthinessValidator(@NonNull MetadataBLOBProvider metadataBLOBProvider) {
        this(Collections.singletonList(metadataBLOBProvider));
    }

    public MetadataBLOBBasedCertPathTrustworthinessValidator(@NonNull List<MetadataBLOBProvider> metadataBLOBProviders) {
        this.metadataBLOBProviders = metadataBLOBProviders;
    }

    @Override
    public void validate(@NonNull AAGUID aaguid, @NonNull CertificateBaseAttestationStatement attestationStatement, @NonNull Instant timestamp) {
        List<MetadataBLOBPayloadEntry> entries;
        if(attestationStatement instanceof FIDOU2FAttestationStatement){
            FIDOU2FAttestationStatement fidou2fAttestationStatement = (FIDOU2FAttestationStatement) attestationStatement;
            byte[] subjectKeyIdentifier = fidou2fAttestationStatement.getX5c().getEndEntityAttestationCertificate().getCertificate().getExtensionValue(SUBJECT_KEY_IDENTIFIER_OID);
            entries = findMetadataBLOBPayloadEntriesByAttestationCertificateKeyIdentifier(subjectKeyIdentifier);
        }
        else {
            entries = findMetadataBLOBPayloadEntriesByAAGUID(aaguid);
        }
        validateAttestationType(attestationStatement, entries);

        for (MetadataBLOBPayloadEntry entry : entries) {
            validateMetadataBLOBPayloadEntry(entry);
        }

        super.validate(aaguid, attestationStatement, timestamp);
    }

    private void validateAttestationType(@NonNull CertificateBaseAttestationStatement attestationStatement, @NonNull List<MetadataBLOBPayloadEntry> entries) {
        List<AuthenticatorAttestationType> authenticatorAttestationTypes = entries.stream()
                .map(MetadataBLOBPayloadEntry::getMetadataStatement)
                .filter(Objects::nonNull)
                .flatMap(item -> item.getAttestationTypes().stream())
                .collect(Collectors.toList());


        boolean isSurrogate = !authenticatorAttestationTypes.isEmpty() &&
                authenticatorAttestationTypes.stream().allMatch(type -> type.equals(AuthenticatorAttestationType.BASIC_SURROGATE));

        if (isSurrogate) {
            if (attestationStatement.getX5c() != null) {
                throw new BadAttestationStatementException("Although AAGUID is registered for surrogate attestation in metadata, x5c contains certificates.");
            }
        }
    }

    @Override
    protected @NonNull Set<TrustAnchor> resolveTrustAnchors(@NonNull AAGUID aaguid) {
        return findMetadataBLOBPayloadEntriesByAAGUID(aaguid).stream()
                .map(MetadataBLOBPayloadEntry::getMetadataStatement)
                .filter(Objects::nonNull)
                .flatMap(item -> item.getAttestationRootCertificates().stream())
                .map(item -> new TrustAnchor(item, null))
                .collect(Collectors.toSet());
    }

    public boolean isNotFidoCertifiedAllowed() {
        return notFidoCertifiedAllowed;
    }

    public void setNotFidoCertifiedAllowed(boolean notFidoCertifiedAllowed) {
        this.notFidoCertifiedAllowed = notFidoCertifiedAllowed;
    }

    public boolean isSelfAssertionSubmittedAllowed() {
        return selfAssertionSubmittedAllowed;
    }

    public void setSelfAssertionSubmittedAllowed(boolean selfAssertionSubmittedAllowed) {
        this.selfAssertionSubmittedAllowed = selfAssertionSubmittedAllowed;
    }


    protected void validateMetadataBLOBPayloadEntry(@NonNull MetadataBLOBPayloadEntry metadataBLOBPayloadEntry) {
        List<StatusReport> statusReports = metadataBLOBPayloadEntry.getStatusReports();
        statusReports.forEach(report -> {
            switch (report.getStatus()) {
                //Info statuses
                case UPDATE_AVAILABLE:
                    // UPDATE_AVAILABLE itself doesn't mean security issue. If security related update is available,
                    // corresponding status report is expected to be added to the report list.
                    break;

                //Certification Related statuses
                case FIDO_CERTIFIED:
                case FIDO_CERTIFIED_L1:
                case FIDO_CERTIFIED_L1_PLUS:
                case FIDO_CERTIFIED_L2:
                case FIDO_CERTIFIED_L2_PLUS:
                case FIDO_CERTIFIED_L3:
                case FIDO_CERTIFIED_L3_PLUS:
                    break;
                case NOT_FIDO_CERTIFIED:
                    if (notFidoCertifiedAllowed) {
                        break;
                    } else {
                        throw new BadStatusException(String.format("FIDO Metadata Service reported `%s` for this authenticator.", report.getStatus()));
                    }
                case SELF_ASSERTION_SUBMITTED:
                    if (selfAssertionSubmittedAllowed) {
                        break;
                    } else {
                        throw new BadStatusException(String.format("FIDO Metadata Service reported `%s` for this authenticator.", report.getStatus()));
                    }

                    // Security Notification statuses
                case ATTESTATION_KEY_COMPROMISE:
                case USER_VERIFICATION_BYPASS:
                case USER_KEY_REMOTE_COMPROMISE:
                case USER_KEY_PHYSICAL_COMPROMISE:
                case REVOKED:
                default:
                    throw new BadStatusException(String.format("FIDO Metadata Service reported `%s` for this authenticator.", report.getStatus()));
            }
        });
    }

    private @NonNull List<MetadataBLOBPayloadEntry> findMetadataBLOBPayloadEntriesByAAGUID(@NonNull AAGUID aaguid) {
        return metadataBLOBProviders.stream()
                .flatMap(provider -> provider.provide().getPayload().getEntries().stream())
                .filter(entry -> Objects.equals(entry.getAaguid(), aaguid))
                .collect(Collectors.toList());
    }

    private @NonNull List<MetadataBLOBPayloadEntry> findMetadataBLOBPayloadEntriesByAttestationCertificateKeyIdentifier(@NonNull byte[] attestationCertificateKeyIdentifier) {
        String hexString = HexUtil.encodeToString(attestationCertificateKeyIdentifier);
        return metadataBLOBProviders.stream()
                .flatMap(provider -> provider.provide().getPayload().getEntries().stream())
                .filter(entry -> entry.getAttestationCertificateKeyIdentifiers() != null && entry.getAttestationCertificateKeyIdentifiers().stream().anyMatch(hexString::equalsIgnoreCase))
                .collect(Collectors.toList());
    }
}
