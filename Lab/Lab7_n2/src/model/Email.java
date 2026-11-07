package model;

public class Email {
    private String time;
    private String sender;
    private String receiver;
    private String subject;
    private String body;
    private String attachedFile;

    public Email(String time, String sender, String receiver, String subject, String body, String attachedFile) {
        this.time = time != null ? time : "";
        this.sender = sender != null ? sender : "";
        this.receiver = receiver != null ? receiver : "";
        this.subject = (subject == null || subject.trim().isEmpty()) ? "(Không có tiêu đề)" : subject.trim();
        this.body = body != null ? body : "";
        this.attachedFile = (attachedFile == null || attachedFile.trim().isEmpty()) ? "None" : attachedFile.trim();
    }

    public String getTime() { return time; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getAttachedFile() { return attachedFile; }
}