package ytdl;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

public class Downloader extends JFrame {
	private enum State {
		GENERIC_ERROR("Error downloading.."),
		CONNECTION_ERROR("Error connecting to server.."),
		VIDEO_PARAM_START("Getting video parameters"),
		VIDEO_PARAM_ERROR("Error getting video parameters.."),
		VIDEO_TITLE_ERROR("Error getting video title.."),
		LINK_PARSE_START("Getting video download link"),
		LINK_PARSE_ERROR("Error getting video download link"),
		DOWNLOAD_START("Downloading video"),
		DOWNLOAD_ERROR("Error downloading video"),
		DOWNLOAD_DONE("Finished"),
		DOWNLOAD_BAD_RESPONSE("Could not complete download"),
		READY("Ready");
		
		private String message;
		
		private State(String message){
			this.message = message;
		}
		
		public String toString(){
			return message;
		}
	}
	
	private static final long serialVersionUID = 2466361469062899471L;

	private HttpClient client;
	private VideoInfo info;
	private JLabel progressLabel;
	private final AtomicBoolean isDownloading = new AtomicBoolean(false);
	private int currentTry = 1;
	private int maxTries = 3;

	public Downloader(HttpClient client, final VideoInfo info) {
		super();
		
		this.client = client;
		this.info = info;
		
		setSize(400, 250);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setResizable(false);
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		setTitle(info.title);

		add(Box.createVerticalStrut(15));
		
		JLabel outputLabel = new JLabel("Save to folder:");
		add(outputLabel);

		final JTextField outputTextField = new JTextField(System.getProperty("user.home") + File.separator + "Desktop");
		add(outputTextField);
		
		add(Box.createVerticalStrut(15));
		
		Font font = Font.getFont("Consolas");
		if (font == null){
			// fallback to courier new
			font = new Font("monospaced", Font.PLAIN, 12);
		}
		
		final ButtonGroup qualityGroup = new ButtonGroup();

		DownloadType t = null;
		for (int i = 0; i < info.downloadTypes.size(); ++i){
			t = info.downloadTypes.get(i);
			String type = t.hasAudio ? "audio" : "";
			type += t.hasVideo ? (type.isEmpty() ? "video" : "/video") : "";
			
			String extension = t.extension == null ? "???" : t.extension;
			
			String quality = t.quality != null ? t.quality : (t.bitrate == -1 ? "???" : (t.bitrate / 1024) + "kbps");
			
			String name = String.format("%-12s (%s); %-12s", type, extension, quality);
			
			JRadioButton qualityButton = new JRadioButton(name, i == 0);
			qualityButton.setFont(font);
			qualityButton.setActionCommand("" + i);
			qualityGroup.add(qualityButton);
			add(qualityButton);
		}	

		add(Box.createVerticalStrut(5));

		final JButton downloadButton = new JButton("Download");
		downloadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!isDownloading.get()){
					isDownloading.set(true);
					downloadButton.setEnabled(false);
					
					final DownloadType type = info.downloadTypes.get(Integer.parseInt(qualityGroup.getSelection().getActionCommand()));
					
					new Thread(){
						public void run(){
							try {
								currentTry = 1;
								
								while (currentTry <= maxTries){
									if (download(type, outputTextField.getText())){
										break;
									}
									++currentTry;
									Thread.sleep(1000);
								}
							}
							catch (Exception e){
								e.printStackTrace();
							}
							
							isDownloading.set(false);
							downloadButton.setEnabled(true);
						};
					}.start();
				}
			}
		});
		add(downloadButton);
		
		add(Box.createVerticalStrut(5));
		
		add(new JSeparator(SwingConstants.HORIZONTAL));
		
		progressLabel = new JLabel();
		add(progressLabel);
		setStatus(State.READY);

		setVisible(true);
		pack();
	}

	public boolean download(DownloadType type, String dest) {
		try {
			setStatus(State.READY);
			
			String fileName = info.title + "." + type.extension;

			return downloadFile(type.url, dest, fileName);
		}
		catch (Exception e) {
			e.printStackTrace();
			setStatusText(State.GENERIC_ERROR, ". " + e.getMessage());
			return false;
		}
	}

	private boolean downloadFile(String downloadLink, String destination, String fileName) throws Exception {
		setStatus(State.DOWNLOAD_START);
		
		//destination = destination.replaceAll("\\\\", "\\").replaceAll("/", "\\").replaceAll("\\", File.separator);
		
		destination += File.separator;
		
		fileName.replaceAll("[/\n\r\t\0\f`\\*?<>|\":\' ]", "");
		String path = destination + fileName;
		File file = new File(path);
		if (file.exists()) {
			file.delete();
		}

		HttpGet request = new HttpGet(downloadLink);
		request.setHeader("User-Agent", InfoParser.USER_AGENT);

		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();

		if (entity == null || response.getStatusLine().getStatusCode() != 200) {
			setStatusText(State.DOWNLOAD_BAD_RESPONSE, ". Code: " + response.getStatusLine().getStatusCode());
			return false;
		}

		InputStream inputStream = entity.getContent();
		FileOutputStream fileStream = new FileOutputStream(file);

		int read = 0;
		byte buffer[] = new byte[2048];

		while ((read = inputStream.read(buffer)) != -1) {
			fileStream.write(buffer, 0, read);
		}

		fileStream.flush();
		fileStream.close();
		
		setStatus(State.DOWNLOAD_DONE);
		return true;
	}

	private void setStatus(State state){
		setStatusText(state, null);
	}
	
	private void setStatusText(State state, String additionalInfo){
		StringBuilder message = new StringBuilder();
		if (state != State.READY && state != State.DOWNLOAD_DONE){
			message.append("(").append(currentTry).append(") "); 
		}
		
		message.append(state);
		
		if (additionalInfo != null){
			message.append(additionalInfo);
		}
		
		progressLabel.setText(message.toString());
	}
}
