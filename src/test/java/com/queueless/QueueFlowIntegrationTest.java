package com.queueless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QueueFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void fullQueueLifecycle() throws Exception {
        // --- owner signs up and creates a queue
        String jwt = body(post("/api/auth/register", "{\"email\":\"Owner@Example.com\",\"password\":\"supersecret\",\"name\":\"Asha\"}", null))
                .get("token").asText();
        JsonNode shop = body(post("/api/owner/shops", "{\"name\":\"Chai Corner\",\"avgServiceMinutes\":3}", jwt));
        long shopId = shop.get("id").asLong();
        String slug = shop.get("slug").asText();
        assertThat(slug).startsWith("chai-corner-");

        // --- two customers join
        JsonNode ann = body(post("/api/public/shops/" + slug + "/join", "{\"name\":\"Ann\"}", null));
        JsonNode bob = body(post("/api/public/shops/" + slug + "/join", "{\"name\":\"Bob\"}", null));
        assertThat(ann.get("number").asInt()).isEqualTo(1);
        assertThat(bob.get("number").asInt()).isEqualTo(2);
        assertThat(bob.get("position").asInt()).isEqualTo(2);
        assertThat(bob.get("etaSeconds").asLong()).isEqualTo(180); // 1 ahead (Ann) * 180s, nobody serving yet

        // --- owner calls next: Ann is served, Bob moves up
        body(post("/api/owner/shops/" + shopId + "/next", null, jwt));
        JsonNode annNow = body(get("/api/public/tickets/" + ann.get("token").asText()));
        JsonNode bobNow = body(get("/api/public/tickets/" + bob.get("token").asText()));
        assertThat(annNow.get("status").asText()).isEqualTo("SERVING");
        assertThat(bobNow.get("position").asInt()).isEqualTo(1);
        assertThat(bobNow.get("nowServing").asInt()).isEqualTo(1);

        // --- owner calls next again: Ann is done, Bob is served
        JsonNode snapshot = body(post("/api/owner/shops/" + shopId + "/next", null, jwt));
        assertThat(snapshot.get("serving").get("number").asInt()).isEqualTo(2);
        assertThat(snapshot.get("servedCount").asLong()).isEqualTo(1);
        assertThat(body(get("/api/public/tickets/" + ann.get("token").asText())).get("status").asText())
                .isEqualTo("DONE");
    }

    @Test
    void cancelOnlyWorksWhileWaiting() throws Exception {
        String jwt = body(post("/api/auth/register", "{\"email\":\"cancel@example.com\",\"password\":\"supersecret\",\"name\":\"C\"}", null))
                .get("token").asText();
        JsonNode shop = body(post("/api/owner/shops", "{\"name\":\"Bakery\",\"avgServiceMinutes\":2}", jwt));
        String slug = shop.get("slug").asText();

        JsonNode t = body(post("/api/public/shops/" + slug + "/join", "{\"name\":\"Dee\"}", null));
        String token = t.get("token").asText();
        assertThat(body(delete("/api/public/tickets/" + token)).get("status").asText()).isEqualTo("CANCELLED");
        mvc.perform(delete("/api/public/tickets/" + token)).andExpect(status().isConflict());
    }

    @Test
    void closedQueueRejectsNewCustomers() throws Exception {
        String jwt = body(post("/api/auth/register", "{\"email\":\"closed@example.com\",\"password\":\"supersecret\",\"name\":\"X\"}", null))
                .get("token").asText();
        JsonNode shop = body(post("/api/owner/shops", "{\"name\":\"Clinic\",\"avgServiceMinutes\":10}", jwt));
        body(post("/api/owner/shops/" + shop.get("id").asLong() + "/toggle-open", null, jwt));

        mvc.perform(MockMvcRequestBuilders.post("/api/public/shops/" + shop.get("slug").asText() + "/join")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerEndpointsRequireAuthAndOwnership() throws Exception {
        mvc.perform(get("/api/owner/shops")).andExpect(status().isUnauthorized());

        String a = body(post("/api/auth/register", "{\"email\":\"a@example.com\",\"password\":\"supersecret\",\"name\":\"A\"}", null))
                .get("token").asText();
        String b = body(post("/api/auth/register", "{\"email\":\"b@example.com\",\"password\":\"supersecret\",\"name\":\"B\"}", null))
                .get("token").asText();
        long shopOfA = body(post("/api/owner/shops", "{\"name\":\"Mine\",\"avgServiceMinutes\":5}", a)).get("id").asLong();

        // B must not be able to touch A's queue, and must not learn that it exists.
        mvc.perform(MockMvcRequestBuilders.post("/api/owner/shops/" + shopOfA + "/next").header("Authorization", "Bearer " + b))
                .andExpect(status().isNotFound());
    }

    // ---- helpers
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String url, String content, String jwt) {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (content != null) {
            req.content(content);
        }
        if (jwt != null) {
            req.header("Authorization", "Bearer " + jwt);
        }
        return req;
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
