package com.example.flags;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks that the real security rules reject the wrong roles before any service is called. */
@WebMvcTest({FlagController.class, UserController.class})
@Import({SecurityConfig.class, AccessControl.class, ApiExceptionHandler.class})
class EndpointSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean FlagService flags;
    @MockBean UserService users;

    private static final String CREATE_FLAG = """
            {"name":"New Checkout","key":"new_checkout","dataType":"BOOLEAN","defaultValue":"false","reason":"prepare"}""";
    private static final String CHANGE_VALUE = "{\"value\":\"true\",\"reason\":\"launch\"}";

    @BeforeEach
    void tokens() {
        for (Role role : Role.values())
            when(auth.authenticate(role.name())).thenReturn(Optional.of(new AuthService.Principal("u", role)));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Role role) {
        return b.header("Authorization", "Bearer " + role.name());
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mvc.perform(get("/api/flags")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/flags").header("Authorization", "Bearer made-up")).andExpect(status().isUnauthorized());
    }

    @Test
    void viewersCanReadButNotChange() throws Exception {
        mvc.perform(as(get("/api/flags"), Role.VIEWER)).andExpect(status().isOk());
        mvc.perform(as(post("/api/flags"), Role.VIEWER).contentType(MediaType.APPLICATION_JSON).content(CREATE_FLAG))
                .andExpect(status().isForbidden());
        mvc.perform(as(put("/api/flags/" + UUID.randomUUID() + "/environments/development"), Role.VIEWER)
                .contentType(MediaType.APPLICATION_JSON).content(CHANGE_VALUE)).andExpect(status().isForbidden());
    }

    @Test
    void developersCanCreateAndChangeFlagsButNotDeleteThem() throws Exception {
        mvc.perform(as(post("/api/flags"), Role.DEVELOPER).contentType(MediaType.APPLICATION_JSON).content(CREATE_FLAG))
                .andExpect(status().isCreated());
        mvc.perform(as(put("/api/flags/" + UUID.randomUUID() + "/environments/development"), Role.DEVELOPER)
                .contentType(MediaType.APPLICATION_JSON).content(CHANGE_VALUE)).andExpect(status().isOk());
        mvc.perform(as(delete("/api/flags/" + UUID.randomUUID()).param("reason", "cleanup"), Role.DEVELOPER))
                .andExpect(status().isForbidden());
    }

    @Test
    void releaseManagersCanDeleteFlags() throws Exception {
        mvc.perform(as(delete("/api/flags/" + UUID.randomUUID()).param("reason", "cleanup"), Role.RELEASE_MANAGER))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyAdministratorsCanManageUsers() throws Exception {
        for (Role role : List.of(Role.VIEWER, Role.DEVELOPER, Role.RELEASE_MANAGER))
            mvc.perform(as(get("/api/users"), role)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/users"), Role.ADMIN)).andExpect(status().isOk());
    }
}
