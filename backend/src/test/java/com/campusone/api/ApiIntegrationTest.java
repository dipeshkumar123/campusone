package com.campusone.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginReturnsRoleAndTokenForBothDemoAccounts() throws Exception {
        JsonNode admin = login("admin@campusone.demo", "Campus@2026");
        JsonNode faculty = login("faculty@campusone.demo", "Faculty@2026");

        assertThat(admin.get("token").asText()).isNotBlank();
        assertThat(admin.at("/user/role").asText()).isEqualTo("ADMIN");
        assertThat(faculty.get("token").asText()).isNotBlank();
        assertThat(faculty.at("/user/role").asText()).isEqualTo("FACULTY");
        assertThat(faculty.at("/user/name").asText()).isEqualTo("Dr. Meera Sahoo");
    }

    @Test
    void wrongPasswordUsesTheGenericInvalidCredentialsError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@campusone.demo","password":"not-the-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void facultyIsBlockedFromFeesAssistantAndAttendanceAdministration() throws Exception {
        String token = login("faculty@campusone.demo", "Faculty@2026").get("token").asText();

        mockMvc.perform(get("/api/fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/assistant/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Which CSE students are below 75% attendance?\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/marks/" + 1 + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"No\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attendance/my-classes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminAssistantIsReadOnlyAndCapsPreviewRows() throws Exception {
        String token = login("admin@campusone.demo", "Campus@2026").get("token").asText();

        MvcResult result = mockMvc.perform(post("/api/assistant/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Which second-year CSE students have attendance below 75%?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.rowCount").isNumber())
                .andReturn();

        JsonNode answer = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(answer.get("rows").size()).isLessThanOrEqualTo(50);
        assertThat(answer.get("scopeNote").asText()).contains("Read-only");
        assertThat(answer.get("summary").asText()).isNotBlank();
    }

    @Test
    void assistantRefusesWriteRequests() throws Exception {
        String token = login("admin@campusone.demo", "Campus@2026").get("token").asText();

        mockMvc.perform(post("/api/assistant/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Delete the fee due for the first student\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUSED"))
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.rowCount").value(0));
    }

    @Test
    void adminCanBrowseFeesStudentsRiskAndDashboard() throws Exception {
        String token = login("admin@campusone.demo", "Campus@2026").get("token").asText();

        mockMvc.perform(get("/api/fees").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.summary.academicYear").isString());
        mockMvc.perform(get("/api/students?page=0&size=5").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
        mockMvc.perform(get("/api/risk").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags").isArray());
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeNote").isString());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
