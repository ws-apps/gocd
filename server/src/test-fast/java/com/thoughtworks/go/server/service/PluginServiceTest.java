/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.service;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.ClearSingleton;
import com.thoughtworks.go.domain.NullPlugin;
import com.thoughtworks.go.domain.Plugin;
import com.thoughtworks.go.plugin.access.common.settings.GoPluginExtension;
import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsConfiguration;
import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsMetadataStore;
import com.thoughtworks.go.plugin.access.common.settings.PluginSettingsProperty;
import com.thoughtworks.go.plugin.access.configrepo.ConfigRepoExtension;
import com.thoughtworks.go.plugin.access.notification.NotificationExtension;
import com.thoughtworks.go.plugin.access.packagematerial.PackageRepositoryExtension;
import com.thoughtworks.go.plugin.access.pluggabletask.TaskExtension;
import com.thoughtworks.go.plugin.access.scm.SCMExtension;
import com.thoughtworks.go.plugin.api.response.validation.ValidationError;
import com.thoughtworks.go.plugin.api.response.validation.ValidationResult;
import com.thoughtworks.go.plugin.domain.common.*;
import com.thoughtworks.go.plugin.domain.notification.NotificationPluginInfo;
import com.thoughtworks.go.plugin.domain.scm.SCMPluginInfo;
import com.thoughtworks.go.plugin.infra.plugininfo.GoPluginDescriptor;
import com.thoughtworks.go.server.dao.PluginSqlMapDao;
import com.thoughtworks.go.server.domain.PluginSettings;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.plugins.builder.DefaultPluginInfoFinder;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.*;

import static com.thoughtworks.go.util.DataStructureUtils.m;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class PluginServiceTest {
    @Mock
    private PackageRepositoryExtension packageRepositoryExtension;
    @Mock
    private SCMExtension scmExtension;
    @Mock
    private TaskExtension taskExtension;
    @Mock
    private NotificationExtension notificationExtension;
    @Mock
    private ConfigRepoExtension configRepoExtension;
    @Mock
    private PluginSqlMapDao pluginDao;
    @Mock
    private SecurityService securityService;
    @Mock
    private EntityHashingService entityHashingService;
    @Mock
    private DefaultPluginInfoFinder defaultPluginInfoFinder;

    private PluginService pluginService;
    private List<GoPluginExtension> extensions;

    @Rule
    public final ClearSingleton clearSingleton = new ClearSingleton();

    @Before
    public void setUp() {
        initMocks(this);

        Map<String, String> configuration = new HashMap<>();
        configuration.put("p1-k1", "v1");
        configuration.put("p1-k2", "");
        configuration.put("p1-k3", null);
        Plugin plugin = new Plugin("plugin-id-1", toJSON(configuration));
        plugin.setId(1L);
        when(pluginDao.findPlugin("plugin-id-1")).thenReturn(plugin);

        when(pluginDao.findPlugin("plugin-id-2")).thenReturn(new NullPlugin());

        PluginSettingsConfiguration configuration1 = new PluginSettingsConfiguration();
        configuration1.add(new PluginSettingsProperty("p1-k1"));
        configuration1.add(new PluginSettingsProperty("p1-k2"));
        configuration1.add(new PluginSettingsProperty("p1-k3"));
        PluginSettingsMetadataStore.getInstance().addMetadataFor("plugin-id-1", PluginConstants.CONFIG_REPO_EXTENSION, configuration1, "template-1");

        PluginSettingsConfiguration configuration2 = new PluginSettingsConfiguration();
        configuration2.add(new PluginSettingsProperty("p2-k1"));
        configuration2.add(new PluginSettingsProperty("p2-k2"));
        configuration2.add(new PluginSettingsProperty("p2-k3"));
        PluginSettingsMetadataStore.getInstance().addMetadataFor("plugin-id-2", PluginConstants.CONFIG_REPO_EXTENSION, configuration2, "template-2");

        when(packageRepositoryExtension.extensionName()).thenReturn(PluginConstants.PACKAGE_MATERIAL_EXTENSION);
        when(scmExtension.extensionName()).thenReturn(PluginConstants.SCM_EXTENSION);
        when(taskExtension.extensionName()).thenReturn(PluginConstants.PLUGGABLE_TASK_EXTENSION);
        when(notificationExtension.extensionName()).thenReturn(PluginConstants.NOTIFICATION_EXTENSION);
        when(configRepoExtension.extensionName()).thenReturn(PluginConstants.CONFIG_REPO_EXTENSION);
        extensions = Arrays.asList(packageRepositoryExtension, scmExtension, taskExtension, notificationExtension, configRepoExtension);
        pluginService = new PluginService(extensions, pluginDao, securityService, entityHashingService, defaultPluginInfoFinder);
    }

    @Test
    public void shouldReturnPluginSettingsFromDbIfItExists() {
        PluginSettings pluginSettings = pluginService.loadStoredPluginSettings("plugin-id-1");

        assertThat(pluginSettings.getPluginSettingsKeys().size(), is(3));
        assertThat(pluginSettings.getValueFor("p1-k1"), is("v1"));
        assertThat(pluginSettings.getValueFor("p1-k2"), is(""));
        assertThat(pluginSettings.getValueFor("p1-k3"), is(nullValue()));
    }

    @Test
    public void shouldReturnNullIfPluginSettingsDoesNotExistInDb() {
        PluginSettings pluginSettings = pluginService.loadStoredPluginSettings("plugin-id-2");

        assertNull(pluginSettings);
    }

    @Test
    public void shouldNotSavePluginSettingsIfUserIsNotAnAdmin() {
        PluginSettings pluginSettings = new PluginSettings("some-plugin");
        Username currentUser = new Username("non-admin");
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(securityService.isUserAdmin(currentUser)).thenReturn(false);

        pluginService.savePluginSettings(currentUser, result, pluginSettings);

        assertThat(result.httpCode(), is(401));
        assertThat(result.toString(), containsString("UNAUTHORIZED_TO_EDIT"));
    }

    @Test
    public void shouldNotSavePluginSettingsIfPluginDoesNotExist() {
        PluginSettings pluginSettings = new PluginSettings("non-existent-plugin");
        Username currentUser = new Username("admin");
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(securityService.isUserAdmin(currentUser)).thenReturn(true);
        for (GoPluginExtension extension : extensions) {
            when(extension.canHandlePlugin("non-existent-plugin")).thenReturn(false);
        }

        pluginService.savePluginSettings(currentUser, result, pluginSettings);

        assertThat(result.httpCode(), is(422));
        assertThat(result.toString(), containsString("Plugin 'non-existent-plugin' does not exist or does not implement settings validation"));
    }

    @Test
    public void shouldNotSavePluginSettingsIfPluginReturnsValidationErrors() {
        PluginSettingsMetadataStore.getInstance().addMetadataFor("some-plugin", PluginConstants.CONFIG_REPO_EXTENSION, null, null);

        PluginSettings pluginSettings = new PluginSettings("some-plugin");
        Username currentUser = new Username("admin");
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(securityService.isUserAdmin(currentUser)).thenReturn(true);

        when(configRepoExtension.canHandlePlugin("some-plugin")).thenReturn(true);
        ValidationResult validationResult = new ValidationResult();
        validationResult.addError(new ValidationError("foo", "foo is a required field"));
        when(configRepoExtension.validatePluginSettings(eq("some-plugin"), any(PluginSettingsConfiguration.class))).thenReturn(validationResult);

        pluginService.savePluginSettings(currentUser, result, pluginSettings);

        assertThat(result.httpCode(), is(422));
        assertThat(pluginSettings.errors().size(), is(1));
        assertThat(pluginSettings.getErrorFor("foo"), is(Arrays.asList("foo is a required field")));
    }

    @Test
    public void shouldSavePluginSettingsToDbIfPluginSettingsAreValidated() {
        Map<String, String> parameterMap = new HashMap<>();
        parameterMap.put("p2-k1", "v1");

        PluginSettings pluginSettings = new PluginSettings("plugin-id-2");
        pluginSettings.populateSettingsMap(parameterMap);

        Username currentUser = new Username("admin");
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(securityService.isUserAdmin(currentUser)).thenReturn(true);

        when(configRepoExtension.canHandlePlugin("plugin-id-2")).thenReturn(true);
        when(configRepoExtension.validatePluginSettings(eq("plugin-id-2"), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());

        pluginService.savePluginSettings(currentUser, result, pluginSettings);

        Plugin plugin = new Plugin("plugin-id-2", toJSON(parameterMap));
        verify(pluginDao).saveOrUpdate(plugin);
    }

    @Test
    public void shouldNotifyPluginThatPluginSettingsHaveChangedAfterSaving() {
        String pluginId = "plugin-id-2";
        Map<String, String> parameterMap = m("p2-k1", "v1");

        PluginSettings pluginSettings = new PluginSettings(pluginId).populateSettingsMap(parameterMap);

        Username currentUser = new Username("admin");
        when(securityService.isUserAdmin(currentUser)).thenReturn(true);

        when(configRepoExtension.canHandlePlugin(pluginId)).thenReturn(true);
        when(configRepoExtension.validatePluginSettings(eq(pluginId), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());

        pluginService.savePluginSettings(currentUser, new HttpLocalizedOperationResult(), pluginSettings);

        Plugin plugin = new Plugin(pluginId, toJSON(parameterMap));
        verify(pluginDao).saveOrUpdate(plugin);
        verify(configRepoExtension).notifyPluginSettingsChange(pluginId, pluginSettings.getSettingsAsKeyValuePair());
    }

    @Test
    public void shouldNotifyTheExtensionWhichHandlesSettingsInAPluginWithMultipleExtensions_WhenPluginSettingsHaveChanged() {
        String pluginId = "plugin-id-2";
        Map<String, String> parameterMap = m("p2-k1", "v1");

        PluginSettings pluginSettings = new PluginSettings(pluginId).populateSettingsMap(parameterMap);

        Username currentUser = new Username("admin");
        when(securityService.isUserAdmin(currentUser)).thenReturn(true);

        when(configRepoExtension.canHandlePlugin(pluginId)).thenReturn(true);
        when(configRepoExtension.validatePluginSettings(eq(pluginId), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());

        pluginService.savePluginSettings(currentUser, new HttpLocalizedOperationResult(), pluginSettings);

        verify(configRepoExtension).notifyPluginSettingsChange(pluginId, pluginSettings.getSettingsAsKeyValuePair());

        verify(scmExtension, never()).canHandlePlugin(pluginId);
        verify(scmExtension, never()).notifyPluginSettingsChange(pluginId, pluginSettings.getSettingsAsKeyValuePair());
        verify(taskExtension, never()).notifyPluginSettingsChange(pluginId, pluginSettings.getSettingsAsKeyValuePair());
        verify(notificationExtension, never()).notifyPluginSettingsChange(pluginId, pluginSettings.getSettingsAsKeyValuePair());
        verify(packageRepositoryExtension, never()).notifyPluginSettingsChange(pluginId, pluginSettings.getSettingsAsKeyValuePair());
    }

    @Test
    public void shouldIgnoreErrorsWhileNotifyingPluginSettingChange() throws Exception {
        Map<String, String> parameterMap = new HashMap<>();
        parameterMap.put("p2-k1", "v1");

        PluginSettings pluginSettings = new PluginSettings("plugin-id-2");
        pluginSettings.populateSettingsMap(parameterMap);

        Username currentUser = new Username("admin");
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(securityService.isUserAdmin(currentUser)).thenReturn(true);

        when(configRepoExtension.canHandlePlugin("plugin-id-2")).thenReturn(true);
        when(configRepoExtension.validatePluginSettings(eq("plugin-id-2"), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());
        doThrow(new RuntimeException()).when(configRepoExtension).notifyPluginSettingsChange("plugin-id-2", pluginSettings.getSettingsAsKeyValuePair());

        pluginService.savePluginSettings(currentUser, result, pluginSettings);

        Plugin plugin = new Plugin("plugin-id-2", toJSON(parameterMap));
        verify(pluginDao).saveOrUpdate(plugin);
        verify(configRepoExtension).notifyPluginSettingsChange("plugin-id-2", pluginSettings.getSettingsAsKeyValuePair());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void shouldCheckForStaleRequestBeforeUpdatingPluginSettings() {
        PluginSettings pluginSettings = new PluginSettings("plugin-id-1");
        Username currentUser = new Username("admin");
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        String md5 = "md5";

        when(entityHashingService.md5ForEntity(pluginSettings)).thenReturn("foo");

        pluginService.updatePluginSettings(currentUser, result, pluginSettings, md5);

        assertThat(result.httpCode(), is(412));
        assertThat(result.toString(), containsString("STALE_RESOURCE_CONFIG"));
    }

    @Test
    public void shouldCallValidationOnPlugin() throws Exception {
        for (GoPluginExtension extension : extensions) {
            String pluginId = UUID.randomUUID().toString();
            PluginSettingsMetadataStore.getInstance().addMetadataFor(pluginId, extension.extensionName(), null, null);

            when(extension.canHandlePlugin(pluginId)).thenReturn(true);
            when(extension.validatePluginSettings(eq(pluginId), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());

            PluginSettings pluginSettings = new PluginSettings(pluginId);
            pluginService.validatePluginSettingsFor(pluginSettings);

            verify(extension).validatePluginSettings(eq(pluginId), any(PluginSettingsConfiguration.class));
        }
    }

    @Test
    public void shouldTalkToPluginForPluginSettingsValidation_ConfigRepo() {
        PluginSettingsMetadataStore.getInstance().addMetadataFor("plugin-id-4", PluginConstants.CONFIG_REPO_EXTENSION, null, null);

        when(configRepoExtension.isConfigRepoPlugin("plugin-id-4")).thenReturn(true);
        when(configRepoExtension.canHandlePlugin("plugin-id-4")).thenReturn(true);
        when(configRepoExtension.validatePluginSettings(eq("plugin-id-4"), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());

        PluginSettings pluginSettings = new PluginSettings("plugin-id-4");
        pluginService.validatePluginSettingsFor(pluginSettings);

        verify(configRepoExtension).validatePluginSettings(eq("plugin-id-4"), any(PluginSettingsConfiguration.class));
    }

    @Test
    public void shouldUpdatePluginSettingsWithErrorsIfExists() {
        PluginSettingsMetadataStore.getInstance().addMetadataFor("plugin-id-4", PluginConstants.NOTIFICATION_EXTENSION, null, null);

        when(notificationExtension.canHandlePlugin("plugin-id-4")).thenReturn(true);
        ValidationResult validationResult = new ValidationResult();
        validationResult.addError(new ValidationError("p4-k1", "m1"));
        validationResult.addError(new ValidationError("p4-k3", "m3"));
        when(notificationExtension.validatePluginSettings(eq("plugin-id-4"), any(PluginSettingsConfiguration.class))).thenReturn(validationResult);

        Map<String, String> parameterMap = new HashMap<>();
        parameterMap.put("p4-k1", "v1");
        parameterMap.put("p4-k2", "v2");
        parameterMap.put("p4-k3", "v3");

        PluginSettings pluginSettings = new PluginSettings("plugin-id-4");
        pluginSettings.populateSettingsMap(parameterMap);
        pluginService.validatePluginSettingsFor(pluginSettings);

        assertThat(pluginSettings.hasErrors(), is(true));
        assertThat(pluginSettings.getErrorFor("p4-k1"), is(Arrays.asList("m1")));
        assertThat(pluginSettings.getErrorFor("p4-k3"), is(Arrays.asList("m3")));
    }

    @Test
    public void shouldNotUpdatePluginSettingsWithErrorsIfNotExists() {
        PluginSettingsMetadataStore.getInstance().addMetadataFor("plugin-id-4", PluginConstants.NOTIFICATION_EXTENSION, null, null);

        when(notificationExtension.canHandlePlugin("plugin-id-4")).thenReturn(true);
        when(notificationExtension.validatePluginSettings(eq("plugin-id-4"), any(PluginSettingsConfiguration.class))).thenReturn(new ValidationResult());

        Map<String, String> parameterMap = new HashMap<>();
        parameterMap.put("p4-k1", "v1");
        parameterMap.put("p4-k2", "v2");
        PluginSettings pluginSettings = new PluginSettings("plugin-id-4");
        pluginSettings.populateSettingsMap(parameterMap);
        pluginService.validatePluginSettingsFor(pluginSettings);

        assertThat(pluginSettings.hasErrors(), is(false));
    }

    @Test
    public void shouldStorePluginSettingsToDBIfItDoesNotExist() {
        Map<String, String> parameterMap = new HashMap<>();
        parameterMap.put("p2-k1", "v1");
        parameterMap.put("p2-k2", "");
        parameterMap.put("p2-k3", null);

        PluginSettings pluginSettings = new PluginSettings("plugin-id-2");
        pluginSettings.populateSettingsMap(parameterMap);

        pluginService.savePluginSettingsFor(pluginSettings);

        Plugin plugin = new Plugin("plugin-id-2", toJSON(parameterMap));
        verify(pluginDao).saveOrUpdate(plugin);
    }

    @Test
    public void shouldUpdatePluginSettingsToDBIfItExists() {
        Map<String, String> parameterMap = new HashMap<>();
        parameterMap.put("p1-k1", "v1");
        parameterMap.put("p1-k2", "v2");
        parameterMap.put("p1-k3", null);

        PluginSettings pluginSettings = new PluginSettings("plugin-id-1");
        pluginSettings.populateSettingsMap(parameterMap);

        pluginService.savePluginSettingsFor(pluginSettings);

        Plugin plugin = new Plugin("plugin-id-1", toJSON(parameterMap));
        plugin.setId(1L);
        verify(pluginDao).saveOrUpdate(plugin);
    }

    @Test
    public void shouldGetPluginInfoFromTheExtensionWhichImplementsPluginSettingsIfThePluginImplementsMultipleExtensions() {
        String pluginId = "plugin-id-1";
        CombinedPluginInfo combinedPluginInfo = new CombinedPluginInfo();
        PluggableInstanceSettings pluginSettings = new PluggableInstanceSettings(Arrays.asList(new PluginConfiguration("key", new Metadata(false, false))));
        GoPluginDescriptor pluginDescriptor = new GoPluginDescriptor(pluginId, "1", null, "location", new File(""), false);
        NotificationPluginInfo notificationPluginInfo = new NotificationPluginInfo(pluginDescriptor, null);
        combinedPluginInfo.add(notificationPluginInfo);
        SCMPluginInfo scmPluginInfo = new SCMPluginInfo(pluginDescriptor, "display_name", new PluggableInstanceSettings(null), pluginSettings);
        combinedPluginInfo.add(scmPluginInfo);

        PluginSettingsMetadataStore.getInstance().addMetadataFor(pluginId, PluginConstants.SCM_EXTENSION, new PluginSettingsConfiguration(), "template-1");
        when(defaultPluginInfoFinder.pluginInfoFor(pluginId)).thenReturn(combinedPluginInfo);
        when(notificationExtension.canHandlePlugin(pluginId)).thenReturn(true);
        when(scmExtension.canHandlePlugin(pluginId)).thenReturn(true);

        PluginInfo pluginInfo = pluginService.pluginInfoForExtensionThatHandlesPluginSettings(pluginId);

        assertTrue(pluginInfo instanceof SCMPluginInfo);
        assertThat(pluginInfo, is(scmPluginInfo));
    }


    @Test
    public void shouldReturnNullForGetPluginInfoIfDoesNotImplementPluginSettings_MultipleExtensionImpl() {
        String pluginId = "plugin-id-1";
        CombinedPluginInfo combinedPluginInfo = new CombinedPluginInfo();
        PluggableInstanceSettings pluginSettings = new PluggableInstanceSettings(Arrays.asList(new PluginConfiguration("key", new Metadata(false, false))));
        GoPluginDescriptor pluginDescriptor = new GoPluginDescriptor(pluginId, "1", null, "location", new File(""), false);
        NotificationPluginInfo notificationPluginInfo = new NotificationPluginInfo(pluginDescriptor, null);
        combinedPluginInfo.add(notificationPluginInfo);
        SCMPluginInfo scmPluginInfo = new SCMPluginInfo(pluginDescriptor, "display_name", new PluggableInstanceSettings(null), pluginSettings);
        combinedPluginInfo.add(scmPluginInfo);

        when(notificationExtension.canHandlePlugin(pluginId)).thenReturn(true);
        when(scmExtension.canHandlePlugin(pluginId)).thenReturn(true);

        assertNull(pluginService.pluginInfoForExtensionThatHandlesPluginSettings(pluginId));
    }

    private String toJSON(Map<String, String> configuration) {
        return new GsonBuilder().serializeNulls().create().toJson(configuration);
    }
}
