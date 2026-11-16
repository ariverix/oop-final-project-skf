package ru.ariverix.finance.infra;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import ru.ariverix.finance.core.WalletRepository;
import ru.ariverix.finance.model.Wallet;

public final class JsonWalletRepository implements WalletRepository {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public JsonWalletRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public Wallet load(String owner) throws IOException {
        Path file = fileFor(owner);
        if (!Files.exists(file)) {
            return null;
        }
        Wallet wallet = mapper.readValue(file.toFile(), Wallet.class);
        if (!owner.equals(wallet.owner())) {
            throw new IOException("В файле неверный владелец кошелька");
        }
        return wallet;
    }

    @Override
    public void save(Wallet wallet) throws IOException {
        write(fileFor(wallet.owner()), wallet);
    }

    @Override
    public void exportTo(Wallet wallet, Path file) throws IOException {
        write(file, wallet);
    }

    @Override
    public Wallet importFrom(Path file) throws IOException {
        return mapper.readValue(file.toFile(), Wallet.class);
    }

    private void write(Path file, Wallet wallet) throws IOException {
        Path path = file.toAbsolutePath();
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "wallet-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), wallet);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path fileFor(String user) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(user.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
