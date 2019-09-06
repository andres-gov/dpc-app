package gov.cms.dpc.attribution.resources;

import gov.cms.dpc.common.entities.TokenEntity;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/Token")
@Produces(value = MediaType.APPLICATION_JSON)
@Consumes(value = MediaType.APPLICATION_JSON)
public abstract class AbstractTokenResource {

    protected AbstractTokenResource() {
        // Not used
    }

    /**
     * Get authentication token for {@link org.hl7.fhir.dstu3.model.Organization}.
     * If no token exists, returns an empty {@link List}
     *
     * @param organizationID - {@link UUID} organization ID
     * @return - {@link List} {@link String} base64 (URL) encoded token
     */
    @GET
    @Path("/{organizationID}")
    public abstract List<TokenEntity> getOrganizationTokens(@NotNull @PathParam("organizationID") UUID organizationID);

    /**
     * Create authentication token for {@link org.hl7.fhir.dstu3.model.Organization}.
     * This token is designed to be long-lived and delegatable.
     *
     * @param organizationID - {@link UUID} organization ID
     * @return - {@link String} base64 (URL) encoded token
     */
    @POST
    @Path("/{organizationID}")
    public abstract String createOrganizationToken(@PathParam("organizationID") @NotNull UUID organizationID);

    @DELETE
    @Path("/{organizationID}/{tokenID}")
    public abstract Response deleteOrganizationToken(@NotNull @PathParam("organizationID") UUID organizationID, @NotNull @PathParam("tokenID") UUID tokenID);

    /**
     * Verify that the provided token is valid
     *
     * @param organizationID - {@link UUID} organization ID
     * @param token          - {@link String} representation of authorization token (optionally base64 encoded)
     * @return - {@link Response} with status {@link Response.Status#OK} if token is valid. {@link Response.Status#UNAUTHORIZED} if token is not valid
     */
    @GET
    @Path("/{organizationID}/verify")
    public abstract Response verifyOrganizationToken(@PathParam("organizationID") UUID organizationID, @NotEmpty @QueryParam("token") String token);
}