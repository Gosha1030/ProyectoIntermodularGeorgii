package georgii.sytnik.thothtasks.data.model;

public class ExternalUser {
    private long externalId;
    private long userId;
    private String externalUserName;
    private Integer ip;
    private Integer port;
    private String type;

    public long getExternalId() { return externalId; }
    public void setExternalId(long externalId) { this.externalId = externalId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getExternalUserName() { return externalUserName; }
    public void setExternalUserName(String externalUserName) { this.externalUserName = externalUserName; }

    public Integer getIp() { return ip; }
    public void setIp(Integer ip) { this.ip = ip; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}