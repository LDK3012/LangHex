package vn.enghexteam.enghex.Model;

import java.util.HashMap;
import java.util.Map;

public class UsernamePasswordUser {
    private String username;
    private String password;

    private static final Map<String, String> sampleAccounts = new HashMap<>();
    static {
        sampleAccounts.put("2174801030046", "123456");
        sampleAccounts.put("2174801030067", "987654");
    }
    public UsernamePasswordUser(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean isValidUsername() {
        return username != null && username.matches("^[a-zA-Z0-9]{1,20}$");
    }

    public boolean isValidPassword() {
        return password != null && !password.isEmpty() && password.equals(password.trim());
    }

    public boolean authenticate() {
        return sampleAccounts.containsKey(username) && sampleAccounts.get(username).equals(password);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }


}
