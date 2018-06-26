'use strict';

/**
 * Copyright 2016 Signal K and Fabian Tollenaar <fabian@signalk.org>.
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

//var debug = require('debug')('signalk-parser-nmea0183/loadSubhooks');
var fs = require('fs');
var path = require('path');

module.exports = function findSubhooks(dir) {
  var fpath = path.join(__dirname, '../hooks', dir);
  var files = fs.readdirSync(fpath);
  var mappings = {};
  files.forEach(function (fname) {
    var subHook = path.basename(fname, '.js');
    mappings[subHook] = fname;
  });
  debug(mappings);
  return mappings;
};