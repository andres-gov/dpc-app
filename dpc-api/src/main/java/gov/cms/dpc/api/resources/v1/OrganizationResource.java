package gov.cms.dpc.api.resources.v1;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import gov.cms.dpc.api.auth.annotations.AdminOperation;
import gov.cms.dpc.api.auth.annotations.PathAuthorizer;
import gov.cms.dpc.api.resources.AbstractOrganizationResource;
import gov.cms.dpc.fhir.annotations.FHIR;
import gov.cms.dpc.fhir.annotations.FHIRParameter;
import io.swagger.annotations.*;
import org.hl7.fhir.dstu3.model.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Api(value = "Organization")
public class OrganizationResource extends AbstractOrganizationResource {

    private final IGenericClient client;

    @Inject
    public OrganizationResource(IGenericClient client) {
        this.client = client;
    }


    @POST
    @Path("/$submit")
    @FHIR
    @Timed
    @ExceptionMetered
    @ApiOperation(hidden = true, value = "Create organization by submitting Bundle")
    @AdminOperation
    @Override
    public Organization submitOrganization(@FHIRParameter(name = "resource") Bundle organizationBundle) {
        // Validate bundle
        validateOrganizationBundle(organizationBundle);

        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("resource").setResource(organizationBundle);
        return this.client
                .operation()
                .onType(Organization.class)
                .named("submit")
                .withParameters(parameters)
                .returnResourceType(Organization.class)
                .encodedJson()
                .execute();
    }

    @Override
    @GET
    @Path("/{organizationID}")
    @FHIR
    @Timed
    @ExceptionMetered
    @PathAuthorizer(type = ResourceType.Organization, pathParam = "organizationID")
    @ApiOperation(value = "Get organization details",
            notes = "FHIR endpoint which returns the Organization resource that is currently registered with the application.",
            authorizations = @Authorization(value = "apiKey"))
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "An organization is only allowed to see their own Organization resource")})
    public Organization getOrganization(@PathParam("organizationID") UUID organizationID) {
        return this.client
                .read()
                .resource(Organization.class)
                .withId(organizationID.toString())
                .encodedJson()
                .execute();
    }

    private void validateOrganizationBundle(Bundle organizationBundle) {
        // Ensure we have an organization
        organizationBundle
                .getEntry()
                .stream()
                .filter(Bundle.BundleEntryComponent::hasResource)
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(resource -> resource.getResourceType().equals(ResourceType.Organization))
                .findAny()
                .orElseThrow(() -> new WebApplicationException("Bundle must include Organization", Response.Status.BAD_REQUEST));


        // Make sure we have some endpoints
        final List<Resource> endpoints = organizationBundle
                .getEntry()
                .stream()
                .filter(Bundle.BundleEntryComponent::hasResource)
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(resource -> resource.getResourceType().equals(ResourceType.Endpoint))
                .collect(Collectors.toList());

        if (endpoints.isEmpty()) {
            throw new WebApplicationException("Organization must have at least 1 endpoint", Response.Status.BAD_REQUEST);
        }
    }
}
