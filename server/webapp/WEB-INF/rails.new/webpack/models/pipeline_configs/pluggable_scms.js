/*
 * Copyright 2016 ThoughtWorks, Inc.
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

var _             = require('lodash');
var Materials     = require('models/pipeline_configs/materials');
var PluginInfos   = require('models/pipeline_configs/plugin_infos');
var PluggableSCMs = {};

PluggableSCMs.init = function () {
  _.each(PluginInfos.filterByType('scm'), function (pluginInfo) {
    PluggableSCMs.Types[pluginInfo.id()] = {
      type:        Materials.Material.PluggableMaterial,
      description: pluginInfo.displayName()
    };
  });
};

PluggableSCMs.Types = {};

module.exports = PluggableSCMs;