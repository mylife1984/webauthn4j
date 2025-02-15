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

package com.webauthn4j.metadata.anchor;

import com.webauthn4j.anchor.TrustAnchorRepository;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.metadata.MetadataBLOBBasedMetadataStatementRepository;
import com.webauthn4j.metadata.MetadataBLOBProvider;

import java.security.cert.TrustAnchor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MetadataBLOBBasedTrustAnchorRepository implements TrustAnchorRepository {

    private final MetadataBLOBBasedMetadataStatementRepository metadataBLOBBasedMetadataStatementRepository;

    public MetadataBLOBBasedTrustAnchorRepository(MetadataBLOBProvider... metadataBLOBProviders) {
        this.metadataBLOBBasedMetadataStatementRepository = new MetadataBLOBBasedMetadataStatementRepository(metadataBLOBProviders);
    }

    @Override
    public Set<TrustAnchor> find(AAGUID aaguid) {
        return metadataBLOBBasedMetadataStatementRepository.find(aaguid).stream()
                .flatMap(item -> item.getAttestationRootCertificates().stream())
                .map(item -> new TrustAnchor(item, null))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<TrustAnchor> find(byte[] attestationCertificateKeyIdentifier) {
        return metadataBLOBBasedMetadataStatementRepository.find(attestationCertificateKeyIdentifier).stream()
                .flatMap(item -> item.getAttestationRootCertificates().stream())
                .map(item -> new TrustAnchor(item, null))
                .collect(Collectors.toSet());
    }

    public boolean isNotFidoCertifiedAllowed() {
        return metadataBLOBBasedMetadataStatementRepository.isNotFidoCertifiedAllowed();
    }

    public void setNotFidoCertifiedAllowed(boolean notFidoCertifiedAllowed) {
        metadataBLOBBasedMetadataStatementRepository.setNotFidoCertifiedAllowed(notFidoCertifiedAllowed);
    }

    public boolean isSelfAssertionSubmittedAllowed() {
        return metadataBLOBBasedMetadataStatementRepository.isSelfAssertionSubmittedAllowed();
    }

    public void setSelfAssertionSubmittedAllowed(boolean selfAssertionSubmittedAllowed) {
        metadataBLOBBasedMetadataStatementRepository.setSelfAssertionSubmittedAllowed(selfAssertionSubmittedAllowed);
    }
}
