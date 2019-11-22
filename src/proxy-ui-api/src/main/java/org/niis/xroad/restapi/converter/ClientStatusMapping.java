/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;

import lombok.Getter;
import org.niis.xroad.restapi.openapi.model.ClientStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between client status in api (enum) and model (string)
 */
@Getter
public enum ClientStatusMapping {
    SAVED(ClientType.STATUS_SAVED, ClientStatus.SAVED),
    REGISTRATION_IN_PROGRESS(ClientType.STATUS_REGINPROG, ClientStatus.REGISTRATION_IN_PROGRESS),
    REGISTERED(ClientType.STATUS_REGISTERED, ClientStatus.REGISTERED),
    DELETION_IN_PROGRESS(ClientType.STATUS_DELINPROG, ClientStatus.DELETION_IN_PROGRESS),
    GLOBAL_ERROR(ClientType.STATUS_GLOBALERR, ClientStatus.GLOBAL_ERROR);

    private final String clientTypeStatus; // ClientType statuses
    private final ClientStatus statusEnum;

    ClientStatusMapping(String clientTypeStatus, ClientStatus statusEnum) {
        this.clientTypeStatus = clientTypeStatus;
        this.statusEnum = statusEnum;
    }

    /**
     * Return matching StatusEnum, if any
     * @param clientTypeStatus
     * @return
     */
    public static Optional<ClientStatus> map(String clientTypeStatus) {
        return getFor(clientTypeStatus).map(ClientStatusMapping::getStatusEnum);
    }

    /**
     * Return matching client type status string, if any
     * @param statusEnum
     * @return
     */
    public static Optional<String> map(ClientStatus statusEnum) {
        return getFor(statusEnum).map(ClientStatusMapping::getClientTypeStatus);
    }

    /**
     * return item matching ClientType status, if any
     * @param clientTypeStatus
     * @return
     */
    public static Optional<ClientStatusMapping> getFor(String clientTypeStatus) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.clientTypeStatus.equals(clientTypeStatus))
                .findFirst();
    }

    /**
     * return item matching ClientType status, if any
     * @param statusEnum
     * @return
     */
    public static Optional<ClientStatusMapping> getFor(ClientStatus statusEnum) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.statusEnum.equals(statusEnum))
                .findFirst();
    }

}
