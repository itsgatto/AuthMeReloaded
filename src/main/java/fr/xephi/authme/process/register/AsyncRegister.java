package fr.xephi.authme.process.register;

import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.bukkit.entity.Player;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.cache.auth.PlayerAuth;
import fr.xephi.authme.cache.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.settings.Messages;
import fr.xephi.authme.settings.Settings;

public class AsyncRegister {

    protected Player player;
    protected String name;
    protected String password;
    protected String email = "";
    private AuthMe plugin;
    private DataSource database;
    private Messages m = Messages.getInstance();

    public AsyncRegister(Player player, String password, String email,
                         AuthMe plugin, DataSource data) {
        this.player = player;
        this.password = password;
        name = player.getName().toLowerCase();
        this.email = email;
        this.plugin = plugin;
        this.database = data;
    }

    protected String getIp() {
        return plugin.getIP(player);
    }

    protected boolean preRegisterCheck() throws Exception {
        String lowpass = password.toLowerCase();
        if (PlayerCache.getInstance().isAuthenticated(name)) {
            m.send(player, "logged_in");
            return false;
        } else if (!Settings.isRegistrationEnabled) {
            m.send(player, "reg_disabled");
            return false;
        } else if (lowpass.contains("delete") || lowpass.contains("where") || lowpass.contains("insert") || lowpass.contains("modify") || lowpass.contains("from") || lowpass.contains("select") || lowpass.contains(";") || lowpass.contains("null") || !lowpass.matches(Settings.getPassRegex)) {
            m.send(player, "password_error");
            return false;
        } else if (lowpass.equalsIgnoreCase(player.getName())) {
            m.send(player, "password_error_nick");
            return false;
        } else if (password.length() < Settings.getPasswordMinLen || password.length() > Settings.passwordMaxLength) {
            m.send(player, "pass_len");
            return false;
        } else if (!Settings.unsafePasswords.isEmpty() && Settings.unsafePasswords.contains(password.toLowerCase())) {
            m.send(player, "password_error_unsafe");
            return false;
        } else if (database.isAuthAvailable(name)) {
            m.send(player, "user_regged");
            return false;
        } else if (Settings.getmaxRegPerIp > 0) {
            if (!plugin.authmePermissible(player, "authme.allow2accounts") && database.getAllAuthsByIp(getIp()).size() >= Settings.getmaxRegPerIp && !getIp().equalsIgnoreCase("127.0.0.1") && !getIp().equalsIgnoreCase("localhost")) {
                m.send(player, "max_reg");
                return false;
            }
        }
        return true;
    }

    public void process() {
        try {
            if (!preRegisterCheck())
                return;
            if (!email.isEmpty() && !email.equals("")) {
                if (Settings.getmaxRegPerEmail > 0) {
                    if (!plugin.authmePermissible(player, "authme.allow2accounts") && database.getAllAuthsByEmail(email).size() >= Settings.getmaxRegPerEmail) {
                        m.send(player, "max_reg");
                        return;
                    }
                }
                emailRegister();
                return;
            }
            passwordRegister();
        } catch (Exception e) {
            ConsoleLogger.showError(e.getMessage());
            ConsoleLogger.writeStackTrace(e);
            m.send(player, "error");
        }
    }

    protected void emailRegister() throws Exception {
        if (Settings.getmaxRegPerEmail > 0) {
            if (!plugin.authmePermissible(player, "authme.allow2accounts") && database.getAllAuthsByEmail(email).size() >= Settings.getmaxRegPerEmail) {
                m.send(player, "max_reg");
                return;
            }
        }
        PlayerAuth auth;
        final String hashnew = PasswordSecurity.getHash(Settings.getPasswordHash, password, name);
        auth = new PlayerAuth(name, hashnew, getIp(), 0, (int) player.getLocation().getX(), (int) player.getLocation().getY(), (int) player.getLocation().getZ(), player.getLocation().getWorld().getName(), email, player.getName());
        if (PasswordSecurity.userSalt.containsKey(name)) {
            auth.setSalt(PasswordSecurity.userSalt.get(name));
        }
        database.saveAuth(auth);
        database.updateEmail(auth);
        database.updateSession(auth);
        plugin.mail.main(auth, password);
        ProcessSyncEmailRegister syncronous = new ProcessSyncEmailRegister(player, plugin);
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, syncronous);

    }

    protected void passwordRegister() {
        PlayerAuth auth;
        String hash;
        try {
            hash = PasswordSecurity.getHash(Settings.getPasswordHash, password, name);
        } catch (NoSuchAlgorithmException e) {
            ConsoleLogger.showError(e.getMessage());
            m.send(player, "error");
            return;
        }
        if (Settings.getMySQLColumnSalt.isEmpty() && !PasswordSecurity.userSalt.containsKey(name)) {
            auth = new PlayerAuth(name, hash, getIp(), new Date().getTime(), "your@email.com", player.getName());
        } else {
            auth = new PlayerAuth(name, hash, PasswordSecurity.userSalt.get(name), getIp(), new Date().getTime(), player.getName());
        }
        if (!database.saveAuth(auth)) {
            m.send(player, "error");
            return;
        }
        if (!Settings.forceRegLogin) {
            PlayerCache.getInstance().addPlayer(auth);
            database.setLogged(name);
        }
        plugin.otherAccounts.addPlayer(player.getUniqueId());
        ProcessSyncronousPasswordRegister syncronous = new ProcessSyncronousPasswordRegister(player, plugin);
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, syncronous);
    }
}