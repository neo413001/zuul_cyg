/*
 * Copyright 2020 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.netty.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.netty.insights.ServerStateHandler.InboundHandler;
import com.netflix.zuul.netty.server.http2.DummyChannelHandler;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerStateHandlerTest {

    private Registry registry;

    private Id connectsId;
    private Id errorsId;
    private Id closesId;

    final String listener = "test-conn-throttled";

    @BeforeEach
    void init() {
        registry = new DefaultRegistry();

        connectsId = registry.createId("server.connections.connect").withTags("id", listener);
        closesId = registry.createId("server.connections.close").withTags("id", listener);
        errorsId = registry.createId("server.connections.errors").withTags("id", listener);
    }

    @Test
    void verifyConnMetrics() {

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(registry, listener));

        Counter connects = (Counter) registry.get(connectsId);
        Counter closes = (Counter) registry.get(closesId);
        Counter errors = (Counter) registry.get(errorsId);

        // Connects X 3
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();
        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();

        assertEquals(3, connects.count());

        // Closes X 1
        channel.pipeline().context(DummyChannelHandler.class).fireChannelInactive();

        assertEquals(3, connects.count());
        assertEquals(1, closes.count());
        assertEquals(0, errors.count());
    }

    @Test
    void setPassportStateOnConnect() {

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(registry, listener));

        channel.pipeline().context(DummyChannelHandler.class).fireChannelActive();

        assertEquals(
                PassportState.SERVER_CH_ACTIVE,
                CurrentPassport.fromChannel(channel).getState());
    }

    @Test
    void setPassportStateOnDisconnect() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new DummyChannelHandler());
        channel.pipeline().addLast(new InboundHandler(registry, listener));

        channel.pipeline().context(DummyChannelHandler.class).fireChannelInactive();

        assertEquals(
                PassportState.SERVER_CH_INACTIVE,
                CurrentPassport.fromChannel(channel).getState());
    }
}
