package ru.ariverix.finance.core;

import java.io.IOException;
import java.nio.file.Path;
import ru.ariverix.finance.model.Wallet;

public interface WalletRepository {
    Wallet load(String owner) throws IOException;

    void save(Wallet wallet) throws IOException;

    void exportTo(Wallet wallet, Path path) throws IOException;

    Wallet importFrom(Path path) throws IOException;
}
