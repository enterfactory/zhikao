package com.zhikao.modules.system;

import com.zhikao.common.BusinessException;
import com.zhikao.entity.SysRole;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SysRoleControllerTest {
    @Test
    void permissionMatrixUsesAuthenticationPolicyAndRejectsUnknownRoles() {
        SysRoleService roles = mock(SysRoleService.class);
        RbacService rbac = mock(RbacService.class);
        SysRole role = new SysRole();
        role.setId(7L);
        role.setRoleCode("subject_lead");
        when(roles.list()).thenReturn(List.of(role));
        when(rbac.permissionCodesOf(List.of("subject_lead"))).thenReturn(List.of("question:view"));
        SysRoleController controller = new SysRoleController(roles, rbac);
        assertEquals(List.of("question:view"), controller.permissions(7L).getData());
        assertThrows(BusinessException.class, () -> controller.permissions(99L));
        verify(rbac, times(1)).permissionCodesOf(anyList());
    }
}
