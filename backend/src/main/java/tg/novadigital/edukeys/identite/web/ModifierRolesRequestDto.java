package tg.novadigital.edukeys.identite.web;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import tg.novadigital.edukeys.identite.domain.RoleCode;

public record ModifierRolesRequestDto(@NotEmpty Set<RoleCode> roles) {
}
