package com.linkedin.datahub.graphql.resolvers.mutate;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;

import com.google.common.collect.ImmutableList;
import com.linkedin.common.urn.CorpuserUrn;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.AddOwnerInput;
import com.linkedin.datahub.graphql.generated.OwnerInput;
import com.linkedin.datahub.graphql.generated.ResourceRefInput;
import com.linkedin.datahub.graphql.resolvers.mutate.util.OwnerUtils;
import com.linkedin.metadata.entity.EntityService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AddOwnerResolver implements DataFetcher<CompletableFuture<Boolean>> {

  private final EntityService _entityService;

  @Override
  public CompletableFuture<Boolean> get(DataFetchingEnvironment environment) throws Exception {
    final AddOwnerInput input = bindArgument(environment.getArgument("input"), AddOwnerInput.class);
    Urn ownerUrn = Urn.createFromString(input.getOwnerUrn());
    Urn targetUrn = Urn.createFromString(input.getResourceUrn());
    OwnerInput.Builder ownerInputBuilder = OwnerInput.builder();
    ownerInputBuilder.setOwnerUrn(input.getOwnerUrn());
    ownerInputBuilder.setOwnerEntityType(input.getOwnerEntityType());
    if (input.getType() != null) {
      ownerInputBuilder.setType(input.getType());
    }
    if (input.getOwnershipTypeUrn() != null) {
      ownerInputBuilder.setOwnershipTypeUrn(input.getOwnershipTypeUrn());
    }

    OwnerInput ownerInput = ownerInputBuilder.build();
    if (!OwnerUtils.isAuthorizedToUpdateOwners(environment.getContext(), targetUrn)) {
      throw new AuthorizationException(
          "Unauthorized to perform this action. Please contact your DataHub administrator.");
    }

    return CompletableFuture.supplyAsync(
        () -> {
          OwnerUtils.validateAddOwnerInput(ownerInput, ownerUrn, _entityService);

          try {

            log.debug("Adding Owner. input: {}", input);

            Urn actor =
                CorpuserUrn.createFromString(
                    ((QueryContext) environment.getContext()).getActorUrn());
            OwnerUtils.addOwnersToResources(
                ImmutableList.of(ownerInput),
                ImmutableList.of(new ResourceRefInput(input.getResourceUrn(), null, null)),
                actor,
                _entityService);
            return true;
          } catch (Exception e) {
            log.error("Failed to add owner to resource with input {}, {}", input, e.getMessage());
            throw new RuntimeException(
                String.format("Failed to add owner to resource with input %s", input), e);
          }
        });
  }
}
