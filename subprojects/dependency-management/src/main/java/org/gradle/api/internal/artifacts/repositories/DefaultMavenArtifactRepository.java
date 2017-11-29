/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DefaultModuleDescriptorArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.service.DefaultServiceRegistry;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.artifacts.repositories.DefaultMetadataSources.mavenDefaults;

public class DefaultMavenArtifactRepository extends AbstractAuthenticationSupportedRepository implements MavenArtifactRepository, ResolutionAwareRepository, PublicationAwareRepository {
    private static final Object NO_OP_VALIDATION_SERVICES = new Object() {
        public DefaultMavenPomMetadataSource.MavenMetadataValidator createValidator() {
            return new DefaultMavenPomMetadataSource.MavenMetadataValidator() {
                @Override
                public boolean validate(String repoName, MutableMavenModuleResolveMetadata metadata, ExternalResourceArtifactResolver artifactResolver) {
                    return true;
                }
            };
        }
    };

    private final Transformer<String, MavenArtifactRepository> describer;
    private final FileResolver fileResolver;
    private final RepositoryTransportFactory transportFactory;
    private Object url;
    private List<Object> additionalUrls = new ArrayList<Object>();
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final ModuleMetadataParser metadataParser;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final FileStore<String> resourcesFileStore;
    private final FileResourceRepository fileResourceRepository;
    private final ExperimentalFeatures experimentalFeatures;
    private final InstantiatorFactory instantiatorFactory;
    private final MetadataSourcesServices metadataSourcesServices;

    public DefaultMavenArtifactRepository(FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                          InstantiatorFactory instantiatorFactory,
                                          FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                          MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                          ModuleMetadataParser metadataParser,
                                          AuthenticationContainer authenticationContainer,
                                          ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                          FileStore<String> resourcesFileStore,
                                          FileResourceRepository fileResourceRepository,
                                          ExperimentalFeatures experimentalFeatures) {
        this(new DefaultDescriber(), fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory,
            artifactFileStore, pomParser, metadataParser, authenticationContainer, moduleIdentifierFactory,
            resourcesFileStore, fileResourceRepository, experimentalFeatures);
    }

    public DefaultMavenArtifactRepository(Transformer<String, MavenArtifactRepository> describer,
                                          FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                          InstantiatorFactory instantiatorFactory,
                                          FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                          MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                          ModuleMetadataParser metadataParser,
                                          AuthenticationContainer authenticationContainer,
                                          ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                          FileStore<String> resourcesFileStore,
                                          FileResourceRepository fileResourceRepository,
                                          ExperimentalFeatures experimentalFeatures) {
        super(instantiatorFactory.decorate(), authenticationContainer, mavenDefaults());
        this.describer = describer;
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.pomParser = pomParser;
        this.metadataParser = metadataParser;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.resourcesFileStore = resourcesFileStore;
        this.fileResourceRepository = fileResourceRepository;
        this.experimentalFeatures = experimentalFeatures;
        this.instantiatorFactory = instantiatorFactory;
        this.metadataSourcesServices = new MetadataSourcesServices();
    }

    @Override
    public String getDisplayName() {
        return describer.transform(this);
    }

    public URI getUrl() {
        return url == null ? null : fileResolver.resolveUri(url);
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    public void setUrl(Object url) {
        this.url = url;
    }

    public Set<URI> getArtifactUrls() {
        Set<URI> result = new LinkedHashSet<URI>();
        for (Object additionalUrl : additionalUrls) {
            result.add(fileResolver.resolveUri(additionalUrl));
        }
        return result;
    }

    public void artifactUrls(Object... urls) {
        additionalUrls.addAll(Lists.newArrayList(urls));
    }

    @Override
    public void setArtifactUrls(Set<URI> urls) {
        setArtifactUrls((Iterable<?>) urls);
    }

    public void setArtifactUrls(Iterable<?> urls) {
        additionalUrls = Lists.newArrayList(urls);
    }

    protected boolean isPreferGradleMetadata() {
        return experimentalFeatures.isEnabled();
    }

    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    protected MavenResolver createRealResolver() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        MavenResolver resolver = createResolver(rootUri);

        for (URI repoUrl : getArtifactUrls()) {
            resolver.addArtifactLocation(repoUrl);
        }
        return resolver;
    }

    private MavenResolver createResolver(URI rootUri) {
        RepositoryTransport transport = getTransport(rootUri.getScheme());
        return new MavenResolver(getName(), rootUri, transport, locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, transport.getResourceAccessor(), resourcesFileStore, getMetadataSources().asImmutable(createMetadataSourcesInstantiator()), MavenMetadataArtifactProvider.INSTANCE);
    }

    protected Instantiator createMetadataSourcesInstantiator() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        registry.addProvider(metadataSourcesServices);
        registry.addProvider(getMetadataValidationServices());
        return instantiatorFactory.inject(registry);
    }

    protected Object getMetadataValidationServices() {
        return NO_OP_VALIDATION_SERVICES;
    }

    protected MetaDataParser<MutableMavenModuleResolveMetadata> getPomParser() {
        return pomParser;
    }

    protected ModuleMetadataParser getMetadataParser() {
        return metadataParser;
    }

    protected FileStore<ModuleComponentArtifactIdentifier> getArtifactFileStore() {
        return artifactFileStore;
    }

    protected FileStore<String> getResourcesFileStore() {
        return resourcesFileStore;
    }

    protected RepositoryTransport getTransport(String scheme) {
        return transportFactory.createTransport(scheme, getName(), getConfiguredAuthentication());
    }

    protected LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> getLocallyAvailableResourceFinder() {
        return locallyAvailableResourceFinder;
    }

    private static class DefaultDescriber implements Transformer<String, MavenArtifactRepository> {
        @Override
        public String transform(MavenArtifactRepository repository) {
            URI url = repository.getUrl();
            if (url == null) {
                return repository.getName();
            }
            return repository.getName() + '(' + url + ')';
        }
    }

    public static class MavenMetadataArtifactProvider implements MetadataArtifactProvider {
        private static final MavenMetadataArtifactProvider INSTANCE = new MavenMetadataArtifactProvider();

        @Override
        public ModuleDescriptorArtifactMetadata getMetaDataArtifactFor(ModuleComponentIdentifier moduleComponentIdentifier) {
            IvyArtifactName ivyArtifactName = getMetaDataArtifactName(moduleComponentIdentifier.getModule());
            DefaultModuleComponentArtifactMetadata defaultModuleComponentArtifactMetadata = new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, ivyArtifactName);
            return new DefaultModuleDescriptorArtifactMetadata(defaultModuleComponentArtifactMetadata);
        }

        @Override
        public IvyArtifactName getMetaDataArtifactName(String moduleName) {
            return new DefaultIvyArtifactName(moduleName, "pom", "pom");
        }
    }

    private class MetadataSourcesServices {
        MetaDataParser<MutableMavenModuleResolveMetadata> createPomParser() {
            return pomParser;
        }

        MetadataArtifactProvider createArtifactProvider() {
            return MavenMetadataArtifactProvider.INSTANCE;
        }

        FileResourceRepository createFileResourceRepository() {
            return fileResourceRepository;
        }

        ImmutableModuleIdentifierFactory createImmutableModuleIdentifierFactory() {
            return moduleIdentifierFactory;
        }

        ExperimentalFeatures createExperimentalFeatures() {
            return experimentalFeatures;
        }

        ModuleMetadataParser createModuleMetadataParser() {
            return metadataParser;
        }

        MutableModuleMetadataFactory<MutableMavenModuleResolveMetadata> createMutableMavenModuleResolveMetadataMutableModuleMetadataFactory() {
            return new MavenMutableModuleMetadataFactory(moduleIdentifierFactory);
        }

    }
}
