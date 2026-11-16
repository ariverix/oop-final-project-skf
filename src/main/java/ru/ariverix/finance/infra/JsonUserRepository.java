package ru.ariverix.finance.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import ru.ariverix.finance.core.UserRepository;

public final class JsonUserRepository implements UserRepository {
    private static final int ITERATIONS = 120000;
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Credentials> accounts;

    public record Credentials(String salt, String hash) {
    }

    public JsonUserRepository(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            this.accounts = mapper.readValue(path.toFile(),
                    new TypeReference<LinkedHashMap<String, Credentials>>() { });
        } else {
            this.accounts = new LinkedHashMap<>();
        }
    }

    @Override
    public synchronized boolean exists(String login) {
        return accounts.containsKey(login);
    }

    @Override
    public synchronized void register(String login, String password) throws IOException {
        if (login == null || !login.matches("[a-zA-Z0-9_]{3,30}")) {
            throw new IllegalArgumentException("Логин: 3–30 символов (латиница, цифры, _)");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 6 символов");
        }
        if (accounts.containsKey(login)) {
            throw new IllegalArgumentException("Логин занят");
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = Base64.getEncoder().encodeToString(hash(password, salt));
        Credentials credentials = new Credentials(Base64.getEncoder().encodeToString(salt), hash);
        Map<String, Credentials> next = new LinkedHashMap<>(accounts);
        next.put(login, credentials);
        write(next);
        accounts.put(login, credentials);
    }

    @Override
    public synchronized boolean authenticate(String login, String password) {
        Credentials credentials = accounts.get(login);
        if (credentials == null || password == null) {
            return false;
        }
        byte[] actual = hash(password, Base64.getDecoder().decode(credentials.salt()));
        byte[] expected = Base64.getDecoder().decode(credentials.hash());
        return MessageDigest.isEqual(actual, expected);
    }

    private static byte[] hash(String password, byte[] salt) {
        PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(specification).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось вычислить хеш пароля", e);
        } finally {
            specification.clearPassword();
        }
    }

    private void write(Map<String, Credentials> data) throws IOException {
        Path full = path.toAbsolutePath();
        Files.createDirectories(full.getParent());
        Path temporary = Files.createTempFile(full.getParent(), "users-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), data);
            try {
                Files.move(temporary, full, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, full, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
