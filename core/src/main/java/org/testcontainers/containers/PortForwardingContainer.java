package org.testcontainers.containers;

import com.github.dockerjava.api.model.ContainerNetwork;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import org.testcontainers.utility.DockerImageName;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public enum PortForwardingContainer {
    INSTANCE;

    private GenericContainer<?> container;

    private final Set<Entry<Integer, Integer>> exposedPorts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final Session sshConnection = createSSHSession();

    @SneakyThrows
    private Session createSSHSession() {
        String password = UUID.randomUUID().toString();
        container =
            new GenericContainer<>(DockerImageName.parse("testcontainers/sshd:1.0.0"))
                .withExposedPorts(22)
                .withEnv("PASSWORD", password)
                .withCommand(
                    "sh",
                    "-c",
                    // Disable ipv6 & Make it listen on all interfaces, not just localhost
                    "echo \"root:$PASSWORD\" | chpasswd && /usr/sbin/sshd -D -o PermitRootLogin=yes -o AddressFamily=inet -o GatewayPorts=yes"
                );
        container.start();

        Session session = new JSch().getSession("root", container.getHost(), container.getMappedPort(22));
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30000);


//        connection.setTCPNoDelay(true);
//        connection.connect(
//            (hostname, port, serverHostKeyAlgorithm, serverHostKey) -> true,
//            (int) Duration.ofSeconds(30).toMillis(),
//            (int) Duration.ofSeconds(30).toMillis()
//        );
//
//        if (!connection.authenticateWithPassword("root", password)) {
//            throw new IllegalStateException("Authentication failed.");
//        }

        return session;
    }

    @SneakyThrows
    public void exposeHostPort(int port) {
        exposeHostPort(port, port);
    }

    @SneakyThrows
    public void exposeHostPort(int hostPort, int containerPort) {
        if (exposedPorts.add(new AbstractMap.SimpleEntry<>(hostPort, containerPort))) {
            getSshConnection().setPortForwardingR(containerPort, "localhost", hostPort);
        }
    }

    void start() {
        getSshConnection();
    }

    Optional<ContainerNetwork> getNetwork() {
        return Optional
            .ofNullable(container)
            .map(GenericContainer::getContainerInfo)
            .flatMap(it -> it.getNetworkSettings().getNetworks().values().stream().findFirst());
    }

    void reset() {
        if (container != null) {
            container.stop();
        }
        container = null;

        ((AtomicReference<?>) (Object) sshConnection).set(null);

        exposedPorts.clear();
    }
}
