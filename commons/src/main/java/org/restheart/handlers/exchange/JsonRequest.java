/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
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
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers.exchange;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import org.restheart.utils.ChannelReader;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonRequest extends Request<JsonElement> {
    private JsonRequest(HttpServerExchange exchange) {
        super(exchange);
    }
    
    public static JsonRequest init(HttpServerExchange exchange) {
        var ret = new JsonRequest(exchange);
        
        try {
            ret.injectContent();
        } catch (IOException ieo) {
            ret.setInError(true);
        }
        
        return ret;
    }
    
    public static JsonRequest wrap(HttpServerExchange exchange) {
        return (JsonRequest) of(exchange);
    }
    
    public void injectContent() throws IOException {
        var json = JsonParser.parseString(ChannelReader
                .read(wrapped.getRequestChannel()));
        
        setContent(json);
    }
}
