package com.example.flags;

import com.example.flags.FlagDtos.EnvironmentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/environments")
@PreAuthorize("hasRole('VIEWER')")
public class EnvironmentController {

    private final EnvironmentRepository environments;

    public EnvironmentController(EnvironmentRepository environments) { this.environments = environments; }

    @GetMapping
    public List<EnvironmentResponse> list() {
        return environments.findAllByOrderBySortOrderAsc().stream()
                .map(e -> new EnvironmentResponse(e.getKey(), e.getName())).toList();
    }
}
