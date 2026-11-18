package com.deng.recommend.security;

import com.deng.recommend.entity.User;
import com.deng.recommend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户状态缓存单元测试：禁用即时生效、缓存窗口与异常降级。
 */
@ExtendWith(MockitoExtension.class)
class UserStatusCacheTest {

    @Mock
    private UserMapper userMapper;

    private static User user(int status) {
        User user = new User();
        user.setId(1L);
        user.setUsername("user001");
        user.setStatus(status);
        return user;
    }

    @Test
    void isActive_normalUser_returnsTrue() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        UserStatusCache cache = new UserStatusCache(userMapper, 1000L);

        assertThat(cache.isActive(1L)).isTrue();
    }

    @Test
    void isActive_disabledUser_returnsFalse() {
        when(userMapper.selectById(1L)).thenReturn(user(0));
        UserStatusCache cache = new UserStatusCache(userMapper, 1000L);

        assertThat(cache.isActive(1L)).isFalse();
    }

    @Test
    void isActive_missingUser_returnsFalse() {
        when(userMapper.selectById(1L)).thenReturn(null);
        UserStatusCache cache = new UserStatusCache(userMapper, 1000L);

        assertThat(cache.isActive(1L)).isFalse();
    }

    @Test
    void isActive_nullUserId_returnsFalse() {
        UserStatusCache cache = new UserStatusCache(userMapper, 1000L);

        assertThat(cache.isActive(null)).isFalse();
        verify(userMapper, times(0)).selectById(null);
    }

    @Test
    void isActive_twiceWithinTtl_queriesDatabaseOnce() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        UserStatusCache cache = new UserStatusCache(userMapper, 1000L);

        cache.isActive(1L);
        cache.isActive(1L);

        verify(userMapper, times(1)).selectById(1L);
    }

    @Test
    void evict_thenIsActive_queriesAgainAndSeesDisabled() {
        when(userMapper.selectById(1L)).thenReturn(user(1), user(0));
        UserStatusCache cache = new UserStatusCache(userMapper, 60_000L);

        assertThat(cache.isActive(1L)).isTrue();
        // 管理员禁用后主动清缓存，下一次立刻读到新状态
        cache.evict(1L);

        assertThat(cache.isActive(1L)).isFalse();
        verify(userMapper, times(2)).selectById(1L);
    }

    @Test
    void isActive_ttlZero_queriesEveryTime() {
        when(userMapper.selectById(1L)).thenReturn(user(1));
        UserStatusCache cache = new UserStatusCache(userMapper, 0L);

        cache.isActive(1L);
        cache.isActive(1L);

        verify(userMapper, times(2)).selectById(1L);
    }

    @Test
    void isActive_negativeTtl_disablesCheck() {
        UserStatusCache cache = new UserStatusCache(userMapper, -1L);

        assertThat(cache.isActive(1L)).isTrue();
        verify(userMapper, times(0)).selectById(1L);
    }

    @Test
    void isActive_dbError_treatsAsActive() {
        when(userMapper.selectById(1L)).thenThrow(new RuntimeException("db down"));
        UserStatusCache cache = new UserStatusCache(userMapper, 0L);

        // 数据库抖动不能把在线用户全部踢下线
        assertThat(cache.isActive(1L)).isTrue();
    }
}
