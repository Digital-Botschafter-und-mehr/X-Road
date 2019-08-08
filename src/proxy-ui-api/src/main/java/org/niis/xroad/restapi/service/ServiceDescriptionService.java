/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.Error;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.exceptions.Warning;
import org.niis.xroad.restapi.exceptions.WsdlNotFoundException;
import org.niis.xroad.restapi.exceptions.WsdlParseException;
import org.niis.xroad.restapi.exceptions.WsdlValidationException;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.wsdl.WsdlParser;
import org.niis.xroad.restapi.wsdl.WsdlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ServiceDescription service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ServiceDescriptionService {

    public static final int DEFAULT_SERVICE_TIMEOUT = 60;
    public static final String DEFAULT_DISABLED_NOTICE = "Out of order";
    public static final String INVALID_WSDL = "clients.invalid_wsdl";
    public static final String WSDL_DOWNLOAD_FAILED = "clients.wsdl_download_failed";
    public static final String WSDL_EXISTS = "clients.wsdl_exists";
    public static final String SERVICE_EXISTS = "clients.service_exists";
    public static final String MALFORMED_URL = "clients.malformed_wsdl_url";
    public static final String WRONG_TYPE = "clients.servicedescription_wrong_type";
    public static final String WARNING_ADDING_SERVICES = "clients.adding_services";
    public static final String WARNING_DELETING_SERVICES = "clients.deleting_services";
    public static final String WARNING_WSDL_VALIDATION_WARNINGS = "clients.wsdl_validation_warnings";
    public static final String ERROR_WARNINGS_DETECTED = "clients.warnings_detected";

    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final ServiceChangeChecker serviceChangeChecker;
    private final WsdlValidator wsdlValidator;

    /**
     * ServiceDescriptionService constructor
     * @param serviceDescriptionRepository
     * @param clientService
     * @param clientRepository
     */
    @Autowired
    public ServiceDescriptionService(ServiceDescriptionRepository serviceDescriptionRepository,
            ClientService clientService, ClientRepository clientRepository,
            ServiceChangeChecker serviceChangeChecker,
            WsdlValidator wsdlValidator) {
        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.serviceChangeChecker = serviceChangeChecker;
        this.wsdlValidator = wsdlValidator;
    }

    /**
     * Disable 1-n services
     * @throws NotFoundException if serviceDescriptions with given ids were not found
     */
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public void disableServices(Collection<Long> serviceDescriptionIds,
            String disabledNotice) {
        toggleServices(false, serviceDescriptionIds, disabledNotice);
    }

    /**
     * Enable 1-n services
     * @throws NotFoundException if serviceDescriptions with given ids were not found
     */
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public void enableServices(Collection<Long> serviceDescriptionIds) {
        toggleServices(true, serviceDescriptionIds, null);
    }

    /**
     * Change 1-n services to enabled/disabled
     * @param serviceDescriptionIds
     * @param disabledNotice
     * @throws NotFoundException if serviceDescriptions with given ids were not found
     */
    private void toggleServices(boolean toEnabled, Collection<Long> serviceDescriptionIds,
            String disabledNotice) {
        List<ServiceDescriptionType> possiblyNullServiceDescriptions = serviceDescriptionRepository
                .getServiceDescriptions(serviceDescriptionIds.toArray(new Long[] {}));

        List<ServiceDescriptionType> serviceDescriptions = possiblyNullServiceDescriptions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (serviceDescriptions.size() != serviceDescriptionIds.size()) {
            Set<Long> foundIds = serviceDescriptions.stream()
                    .map(serviceDescriptionType -> serviceDescriptionType.getId())
                    .collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(serviceDescriptionIds);
            missingIds.removeAll(foundIds);
            throw new NotFoundException("Service descriptions with ids " + missingIds
                    + " not found");
        }

        serviceDescriptions.stream()
                .forEach(serviceDescriptionType -> {
                    serviceDescriptionType.setDisabled(!toEnabled);
                    if (!toEnabled) {
                        serviceDescriptionType.setDisabledNotice(disabledNotice);
                    }
                    serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);
                });
    }

    /**
     * Delete one ServiceDescription
     * @throws NotFoundException if serviceDescriptions with given id was not found
     */
    @PreAuthorize("hasAuthority('DELETE_WSDL')")
    public void deleteServiceDescription(Long id) {
        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionType == null) {
            throw new NotFoundException("Service description with id " + id + " not found");
        }
        ClientType client = serviceDescriptionType.getClient();
        client.getServiceDescription().remove(serviceDescriptionType);
        clientRepository.saveOrUpdate(client);
    }

    /**
     * Add a new WSDL ServiceDescription
     * @param clientId
     * @param url
     * @param ignoreWarnings
     * @throws InvalidParametersException if URL is malformed
     * @throws ConflictException          URL already exists
     */
    @PreAuthorize("hasAuthority('ADD_WSDL')")
    public void addWsdlServiceDescription(ClientId clientId, String url, boolean ignoreWarnings) {
        ClientType client = clientService.getClient(clientId);
        if (client == null) {
            throw new NotFoundException("Client with id " + clientId.toShortString() + " not found");
        }

        // check for valid url (is this not enough??)
        if (!FormatUtils.isValidUrl(url)) {
            throw new BadRequestException("Malformed URL", new Error(MALFORMED_URL));
        }

        // check if wsdl already exists
        checkForExistingWsdl(client, url);

        // parse wsdl
        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check if services exist
        checkForExistingServices(client, parsedServices);

        // validate wsdl
        List<String> validationWarnings = validateWsdl(url);

        if (!ignoreWarnings && !validationWarnings.isEmpty()) {
            Warning validationWarning = new Warning(WARNING_WSDL_VALIDATION_WARNINGS,
                    validationWarnings);
            throw new BadRequestException(new Error(ERROR_WARNINGS_DETECTED),
                    Collections.singletonList(validationWarning));
        }

        // create a new ServiceDescription with parsed services
        ServiceDescriptionType serviceDescriptionType = buildWsdlServiceDescription(client, parsedServices, url);

        client.getServiceDescription().add(serviceDescriptionType);
        clientRepository.saveOrUpdate(client);
    }

    /**
     * Update the WSDL url of the selected ServiceDescription
     * @param id
     * @param url the new url
     * @return ServiceDescriptionType
     */
    @PreAuthorize("hasAuthority('EDIT_WSDL')")
    public ServiceDescriptionType updateWsdlUrl(Long id, String url, boolean ignoreWarnings) {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new NotFoundException("Service description with id " + id.toString() + " not found");
        }

        // Shouldn't be able to edit e.g. REST service descriptions with a WSDL URL
        if (serviceDescriptionType.getType() != DescriptionType.WSDL) {
            throw new BadRequestException("Existing service description (id: " + id.toString() + " is not WSDL",
                    new Error(WRONG_TYPE));
        }

        if (!FormatUtils.isValidUrl(url)) {
            throw new BadRequestException("Malformed URL", new Error(MALFORMED_URL));
        }

        ClientType client = serviceDescriptionType.getClient();

        checkForExistingWsdl(client, url);

        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check for existing services but exclude the services in the ServiceDescription that we are updating
        checkForExistingServices(client, parsedServices, id);

        List<String> validationWarningMessages = validateWsdl(url);
        List<Warning> warnings = new ArrayList<>();

        if (!validationWarningMessages.isEmpty()) {
            Warning validationWarning = new Warning(WARNING_WSDL_VALIDATION_WARNINGS,
                    validationWarningMessages);
            warnings.add(validationWarning);
        }

        serviceDescriptionType.setRefreshedDate(new Date());
        serviceDescriptionType.setUrl(url);

        // create services
        List<ServiceType> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        // find what services were added or removed
        ServiceChangeChecker.ServiceChanges serviceChanges = serviceChangeChecker.check(
                serviceDescriptionType.getService(),
                newServices);

        if (!serviceChanges.isEmpty()) {
            warnings.addAll(createServiceChangeWarnings(serviceChanges));
        }

        if (!ignoreWarnings && !warnings.isEmpty()) {
            throw new BadRequestException(new Error(ERROR_WARNINGS_DETECTED),
                    warnings);
        }

        // replace all old services with the new ones
        serviceDescriptionType.getService().clear();
        serviceDescriptionType.getService().addAll(newServices);
        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);

        return serviceDescriptionType;
    }

    /**
     * @return warnings about adding or deleting services
     */
    private List<Warning> createServiceChangeWarnings(ServiceChangeChecker.ServiceChanges changes) {
        List<Warning> warnings = new ArrayList<>();
        if (!CollectionUtils.isEmpty(changes.getAddedServices())) {
            Warning addedServicesWarning = new Warning(WARNING_ADDING_SERVICES,
                    changes.getAddedServices());
            warnings.add(addedServicesWarning);
        }
        if (!CollectionUtils.isEmpty(changes.getRemovedServices())) {
            Warning deletedServicesWarning = new Warning(WARNING_DELETING_SERVICES,
                    changes.getRemovedServices());
            warnings.add(deletedServicesWarning);
        }
        return warnings;
    }

    private void checkForExistingWsdl(ClientType client, String url) throws ConflictException {
        client.getServiceDescription().forEach(serviceDescription -> {
            if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                throw new ConflictException("WSDL URL already exists", new Error(WSDL_EXISTS));
            }
        });
    }

    private ServiceDescriptionType buildWsdlServiceDescription(ClientType client,
            Collection<WsdlParser.ServiceInfo> parsedServices, String url) {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url, DescriptionType.WSDL);

        // create services
        List<ServiceType> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        serviceDescriptionType.getService().addAll(newServices);

        return serviceDescriptionType;
    }

    /**
     * Get one ServiceDescriptionType by id
     * @param id
     * @return ServiceDescriptionType
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ServiceDescriptionType getServiceDescriptiontype(Long id) {
        return serviceDescriptionRepository.getServiceDescription(id);
    }

    private ServiceDescriptionType getServiceDescriptionOfType(ClientType client, String url,
            DescriptionType descriptionType) {
        ServiceDescriptionType serviceDescriptionType = new ServiceDescriptionType();
        serviceDescriptionType.setClient(client);
        serviceDescriptionType.setDisabled(true);
        serviceDescriptionType.setDisabledNotice(DEFAULT_DISABLED_NOTICE);
        serviceDescriptionType.setRefreshedDate(new Date());
        serviceDescriptionType.setType(descriptionType);
        serviceDescriptionType.setUrl(url);
        return serviceDescriptionType;
    }

    private ServiceType serviceInfoToServiceType(WsdlParser.ServiceInfo serviceInfo,
            ServiceDescriptionType serviceDescriptionType) {
        ServiceType newService = new ServiceType();
        newService.setServiceCode(serviceInfo.name);
        newService.setServiceVersion(serviceInfo.version);
        newService.setTitle(serviceInfo.title);
        newService.setUrl(serviceInfo.url);
        newService.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        newService.setServiceDescription(serviceDescriptionType);
        return newService;
    }

    private Collection<WsdlParser.ServiceInfo> parseWsdl(String url) throws BadRequestException {
        Collection<WsdlParser.ServiceInfo> parsedServices;
        try {
            parsedServices = WsdlParser.parseWSDL(url);
        } catch (WsdlParseException e) {
            throw new BadRequestException(e, new Error(INVALID_WSDL));
        } catch (WsdlNotFoundException e) {
            throw new BadRequestException(e, new Error(WSDL_DOWNLOAD_FAILED));
        }
        return parsedServices;
    }

    /**
     * Should return warnings instead of throwing them, to make it possible
     * to report both "changed services" and "validation warnings" warnings
     * @param url
     * @return list of validation warnings that can be ignored by choice
     *
     * @throws BadRequestException
     */
    private List<String> validateWsdl(String url) throws BadRequestException {
        try {
            return wsdlValidator.executeValidator(url);
        } catch (WsdlValidationException e) {
            log.error("WSDL validation failed", e);
            throw new BadRequestException(e, e.getError(), e.getWarnings());
        }
    }

    private List<ServiceType> getClientsExistingServices(ClientType client, Long idToSkip) {
        return client.getServiceDescription()
                .stream()
                .filter(serviceDescriptionType -> !Objects.equals(serviceDescriptionType.getId(), idToSkip))
                .map(ServiceDescriptionType::getService)
                .flatMap(List::stream).collect(Collectors.toList());
    }

    private List<ServiceType> getClientsExistingServices(ClientType client) {
        return getClientsExistingServices(client, null);
    }

    private void checkForExistingServices(ClientType client, Collection<WsdlParser.ServiceInfo> parsedServices,
            Long idToSkip) throws ConflictException {
        List<ServiceType> existingServices = getClientsExistingServices(client, idToSkip);

        Set<ServiceType> conflictedServices = parsedServices
                .stream()
                .flatMap(newService -> existingServices
                        .stream()
                        .filter(existingService -> FormatUtils.getServiceFullName(existingService)
                                .equalsIgnoreCase(FormatUtils.getServiceFullName(newService))))
                .collect(Collectors.toSet());

        // throw error with service metadata if conflicted
        if (!conflictedServices.isEmpty()) {
            List<String> errorMetadata = new ArrayList();
            for (ServiceType conflictedService: conflictedServices) {
                // error metadata contains service name and service description url
                errorMetadata.add(FormatUtils.getServiceFullName(conflictedService));
                errorMetadata.add(conflictedService.getServiceDescription().getUrl());
            }
            throw new ConflictException(new Error(SERVICE_EXISTS, errorMetadata));
        }
    }

    private void checkForExistingServices(ClientType client,
            Collection<WsdlParser.ServiceInfo> parsedServices) throws ConflictException {
        checkForExistingServices(client, parsedServices, null);
    }

    /**
     * Refresh a ServiceDescription
     * @param id
     * @param ignoreWarnings
     * @return {@link ServiceDescriptionType}
     */
    @PreAuthorize("hasAuthority('REFRESH_WSDL')")
    public ServiceDescriptionType refreshServiceDescription(Long id, boolean ignoreWarnings) {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new NotFoundException("Service description with id " + id.toString() + " not found");
        }

        if (serviceDescriptionType.getType() == DescriptionType.WSDL) {
            return refreshWsdl(serviceDescriptionType, ignoreWarnings);
        }

        // we only have two types at the moment so the type must be OPENAPI3 if we end up this far
        throw new NotImplementedException("REST ServiceDescription refresh not implemented yet");
    }

    private ServiceDescriptionType refreshWsdl(ServiceDescriptionType serviceDescriptionType, boolean ignoreWarnings) {
        ClientType client = serviceDescriptionType.getClient();

        String wsdlUrl = serviceDescriptionType.getUrl();

        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(wsdlUrl);

        checkForExistingServices(client, parsedServices, serviceDescriptionType.getId());

        List<String> validationWarningMessages = validateWsdl(wsdlUrl);
        List<Warning> warnings = new ArrayList<>();

        if (!validationWarningMessages.isEmpty()) {
            Warning validationWarning = new Warning(WARNING_WSDL_VALIDATION_WARNINGS,
                    validationWarningMessages);
            warnings.add(validationWarning);
        }

        serviceDescriptionType.setRefreshedDate(new Date());

        // create services
        List<ServiceType> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        // find what services were added or removed
        ServiceChangeChecker.ServiceChanges serviceChanges = serviceChangeChecker.check(
                serviceDescriptionType.getService(),
                newServices);

        if (!serviceChanges.isEmpty()) {
            warnings.addAll(createServiceChangeWarnings(serviceChanges));
        }

        if (!ignoreWarnings && !warnings.isEmpty()) {
            throw new BadRequestException(new Error(ERROR_WARNINGS_DETECTED),
                    warnings);
        }

        // replace all old services with the new ones
        serviceDescriptionType.getService().clear();
        serviceDescriptionType.getService().addAll(newServices);
        clientRepository.saveOrUpdate(client);

        return serviceDescriptionType;
    }
}
