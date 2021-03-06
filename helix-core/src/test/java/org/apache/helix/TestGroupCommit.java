package org.apache.helix;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.helix.mock.MockBaseDataAccessor;

public class TestGroupCommit {
  // @Test
  public void testGroupCommit() throws InterruptedException {
    final BaseDataAccessor<ZNRecord> accessor = new MockBaseDataAccessor();
    final GroupCommit commit = new GroupCommit();
    ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(400);
    for (int i = 0; i < 2400; i++) {
      Runnable runnable = new MyClass(accessor, commit, i);
      newFixedThreadPool.submit(runnable);
    }
    Thread.sleep(10000);
    System.out.println(accessor.get("test", null, 0));
    System.out.println(accessor.get("test", null, 0).getSimpleFields().size());
  }

}

class MyClass implements Runnable {
  private final BaseDataAccessor<ZNRecord> store;
  private final GroupCommit commit;
  private final int i;

  public MyClass(BaseDataAccessor<ZNRecord> store, GroupCommit commit, int i) {
    this.store = store;
    this.commit = commit;
    this.i = i;
  }

  @Override
  public void run() {
    // System.out.println("START " + System.currentTimeMillis() + " --"
    // + Thread.currentThread().getId());
    ZNRecord znRecord = new ZNRecord("test");
    znRecord.setSimpleField("test_id" + i, "" + i);
    commit.commit(store, 0, "test", znRecord);
    store.get("test", null, 0).getSimpleField("");
    // System.out.println("END " + System.currentTimeMillis() + " --"
    // + Thread.currentThread().getId());
  }

}
