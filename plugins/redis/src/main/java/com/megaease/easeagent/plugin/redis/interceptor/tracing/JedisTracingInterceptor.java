/*
 * Copyright (c) 2017, MegaEase
 * All rights reserved.
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

package com.megaease.easeagent.plugin.redis.interceptor.tracing;

import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.annotation.AdviceTo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.redis.RedisPlugin;
import com.megaease.easeagent.plugin.redis.advice.JedisAdvice;
import redis.clients.jedis.Jedis;

@AdviceTo(value = JedisAdvice.class, qualifier = "default", plugin = RedisPlugin.class)
public class JedisTracingInterceptor extends CommonRedisTracingInterceptor {

    @Override
    public void doTraceBefore(MethodInfo methodInfo, Context context) {
        Jedis invoker = (Jedis) methodInfo.getInvoker();
        String name = invoker.getClass().getSimpleName() + "." + methodInfo.getMethod();
        String cmd = methodInfo.getMethod();
        this.startTracing(context, name, null, cmd);
    }
}
