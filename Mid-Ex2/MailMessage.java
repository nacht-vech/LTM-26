import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MailMessage implements Serializable {
    private static final long serialVersionUID = 3L; 

    public String sender; 
    public String receiver; 
    public String subject; 
    public String textContent; 
    public List<String> fileNames; 
    public List<byte[]> fileDatas; 
    public String timestamp; 

    public MailMessage(String sender, String receiver, String subject, String textContent) {
        this.sender = sender; 
        this.receiver = receiver;
        this.subject = subject;
        this.textContent = textContent; 
        this.fileNames = new ArrayList<>();
        this.fileDatas = new ArrayList<>();
        this.timestamp = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(new Date());
    }

    public void addAttachment(String fileName, byte[] fileData) {
        this.fileNames.add(fileName);
        this.fileDatas.add(fileData);
    }
    
    public boolean hasAttachments() {
        return fileNames != null && !fileNames.isEmpty();
    }
}