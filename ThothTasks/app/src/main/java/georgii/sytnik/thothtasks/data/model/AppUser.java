package georgii.sytnik.thothtasks.data.model;

import georgii.sytnik.thothtasks.data.enumtype.UserType;

public class AppUser {
    private long userId;
    private Long defaultTaskGroupId;
    private String userName;
    private String password;
    private UserType type;
    private Integer ip;
    private boolean passwordRequired;
    private boolean confirmRequired;
    private Integer port;
    private String settingsJson;

    public AppUser() {}

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public Long getDefaultTaskGroupId() { return defaultTaskGroupId; }
    public void setDefaultTaskGroupId(Long defaultTaskGroupId) { this.defaultTaskGroupId = defaultTaskGroupId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserType getType() { return type; }
    public void setType(UserType type) { this.type = type; }

    public Integer getIp() { return ip; }
    public void setIp(Integer ip) { this.ip = ip; }

    public boolean isPasswordRequired() { return passwordRequired; }
    public void setPasswordRequired(boolean passwordRequired) { this.passwordRequired = passwordRequired; }

    public boolean isConfirmRequired() { return confirmRequired; }
    public void setConfirmRequired(boolean confirmRequired) { this.confirmRequired = confirmRequired; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getSettingsJson() { return settingsJson; }
    public void setSettingsJson(String settingsJson) { this.settingsJson = settingsJson; }
}