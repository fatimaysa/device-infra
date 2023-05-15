/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.deviceaction.common.utils;

import com.google.common.collect.ImmutableMultimap;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import java.io.File;
import java.util.List;

/** A {@link Resolver} that only applies to a particular type of file spec. */
public abstract class SimpleResolver implements Resolver {

  /** Checks if the resolver applies to the file spec. */
  abstract boolean appliesTo(FileSpec fileSpec);

  /** Resolves a single file spec if it is applied. */
  abstract File resolveFile(FileSpec fileSpec) throws DeviceActionException, InterruptedException;

  /** See {@link Resolver#resolve(List)}. */
  @Override
  public ImmutableMultimap<String, File> resolve(List<FileSpec> fileSpecs)
      throws DeviceActionException, InterruptedException {
    ImmutableMultimap.Builder<String, File> builder = ImmutableMultimap.builder();
    for (FileSpec spec : fileSpecs) {
      File resolved = resolveFile(spec);
      if (resolved.exists()) {
        builder.put(spec.getTag(), resolved);
      }
    }
    return builder.build();
  }
}
