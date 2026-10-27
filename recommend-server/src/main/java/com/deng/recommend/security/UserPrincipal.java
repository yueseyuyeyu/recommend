package com.deng.recommend.security;

import com.deng.recommend.entity.enums.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录主体，承载当前登录用户的核心信息。
 */
@Getter
@Setter
public class UserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    /** BCrypt 哈希，绝不保存明文 */
    private String passwordHash;

    private UserRole role;

    private boolean enabled;

    public UserPrincipal() {
    }

    public UserPrincipal(Long userId, String username, String passwordHash, UserRole role, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
