/*
 * Copyright (c) 2021 MegaEase
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

package com.megaease.easeagent.plugin.mongodb.interceptor;

import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.config.AutoRefreshPluginConfigImpl;
import com.megaease.easeagent.plugin.api.middleware.MiddlewareConstants;
import com.megaease.easeagent.plugin.api.middleware.Type;
import com.megaease.easeagent.plugin.api.trace.Span;
import com.mongodb.MongoSocketException;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class TraceHelper {
    public static final String SPAN_KEY = TraceHelper.class.getName() + "-Span";

    // See https://docs.mongodb.com/manual/reference/command for the command reference
    static final Set<String> COMMANDS_WITH_COLLECTION_NAME = new LinkedHashSet<>(Arrays.asList(
        "aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
        "insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
        "dropIndexes", "killCursors", "listIndexes", "reIndex"));

    public static void commandStarted(Context context, AutoRefreshPluginConfigImpl config, CommandStartedEvent event) {
        if (!config.getConfig().enabled()) {
            return;
        }
        Span span = context.nextSpan();
        context.put(SPAN_KEY, span);

        String databaseName = event.getDatabaseName();
        if ("admin".equals(databaseName)) return; // don't trace commands like "endSessions"

        if (span == null || span.isNoop()) return;

        String commandName = event.getCommandName();
        BsonDocument command = event.getCommand();
        String collectionName = getCollectionName(command, commandName);

        span.name(getSpanName(commandName, collectionName))
            .kind(Span.Kind.CLIENT)
            .remoteServiceName("mongodb-" + databaseName)
            .tag("mongodb.command", commandName)
            .tag(MiddlewareConstants.TYPE_TAG_NAME, Type.MONGODB.getRemoteType())
        ;

        if (collectionName != null) {
            span.tag("mongodb.collection", collectionName);
        }

        ConnectionDescription connectionDescription = event.getConnectionDescription();
        if (connectionDescription != null) {
            ConnectionId connectionId = connectionDescription.getConnectionId();
            if (connectionId != null) {
                span.tag("mongodb.cluster_id", connectionId.getServerId().getClusterId().getValue());
            }

            try {
                InetSocketAddress socketAddress =
                    connectionDescription.getServerAddress().getSocketAddress();
                span.remoteIpAndPort(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
            } catch (MongoSocketException ignored) {

            }
        }

        span.start();
    }

    public static void commandSucceeded(Context context, CommandSucceededEvent event) {
        Span span = context.get(SPAN_KEY);
        if (span == null) {
            return;
        }
        span.finish();
    }

    public static void commandFailed(Context context, CommandFailedEvent event) {
        Span span = context.get(SPAN_KEY);
        if (span == null) {
            return;
        }
        span.error(event.getThrowable());
        span.finish();
    }

    public static String getCollectionName(BsonDocument command, String commandName) {
        if (COMMANDS_WITH_COLLECTION_NAME.contains(commandName)) {
            String collectionName = getNonEmptyBsonString(command.get(commandName));
            if (collectionName != null) {
                return collectionName;
            }
        }
        // Some other commands, like getMore, have a field like {"collection": collectionName}.
        return getNonEmptyBsonString(command.get("collection"));
    }

    /**
     * @return trimmed string from {@code bsonValue} or null if the trimmed string was empty or the
     * value wasn't a string
     */
    protected static String getNonEmptyBsonString(BsonValue bsonValue) {
        if (bsonValue == null || !bsonValue.isString()) return null;
        String stringValue = bsonValue.asString().getValue().trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    protected static String getSpanName(String commandName, String collectionName) {
        if (collectionName == null) return commandName;
        return commandName + " " + collectionName;
    }

}
