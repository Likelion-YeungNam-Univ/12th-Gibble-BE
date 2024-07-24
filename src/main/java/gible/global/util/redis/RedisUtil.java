package gible.global.util.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class RedisUtil {
    private final RedisTemplate<String, Object> redisTemplate;

    public void save(String key, Object value){
        redisTemplate.opsForValue().set(key, value);
    }

    public void saveExpire(String key, Long seconds){
        redisTemplate.opsForValue().set(key, String.valueOf(seconds));
    }

    public boolean get(String key){
        return redisTemplate.opsForValue().get(key) == null;
    }

    public void delete(String userId) {
        redisTemplate.delete(userId);
    }
}
