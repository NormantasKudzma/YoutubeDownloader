package ytdl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public class Downloader extends JFrame {
	private static final long serialVersionUID = -1034923882983690258L;

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
	
	public static class Pair {
		String key;
		String value;

		public Pair(String k, String v) {
			key = k;
			value = v;
		}
	}

	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.2.13) Gecko/20101203 Firefox/3.6.13";

	private CloseableHttpClient client;
	private JLabel progressLabel;
	private final AtomicBoolean isDownloading = new AtomicBoolean(false);
	private int currentTry = 1;
	private int maxTries = 10;

	public Downloader() {
		super();
		setSize(400, 250);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("Youtube video downloader");
		setResizable(false);
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

		JLabel urlLabel = new JLabel("Youtube video url:");
		add(urlLabel);

		final JTextField urlTextField = new JTextField();
		add(urlTextField);

		JLabel outputLabel = new JLabel("Save to folder:");
		add(outputLabel);

		final JTextField outputTextField = new JTextField(System.getProperty("user.home") + File.separator + "Desktop");
		add(outputTextField);
		
		final ButtonGroup qualityGroup = new ButtonGroup();
		JRadioButton hdQuality = new JRadioButton("720p");
		hdQuality.setActionCommand("hd720");
		JRadioButton medQuality = new JRadioButton("480p");
		medQuality.setActionCommand("medium");
		JRadioButton lowQuality = new JRadioButton("240p");
		lowQuality.setActionCommand("small");
		
		qualityGroup.add(hdQuality);
		qualityGroup.add(medQuality);
		qualityGroup.add(lowQuality);
		qualityGroup.setSelected(hdQuality.getModel(), true);
		
		add(hdQuality);
		add(medQuality);
		add(lowQuality);

		final JButton downloadButton = new JButton("Download");
		downloadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!isDownloading.get()){
					isDownloading.set(true);
					downloadButton.setEnabled(false);
					
					new Thread(){
						public void run(){
							try {
								currentTry = 1;
								
								while (currentTry <= maxTries){
									if (download(urlTextField.getText(), outputTextField.getText(), qualityGroup.getSelection().getActionCommand(), "mp4"))
									{
										break;
									}
									System.out.println("Download failed " + currentTry);
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
		
		add(new JSeparator(SwingConstants.HORIZONTAL));
		
		progressLabel = new JLabel();
		add(progressLabel);
		setStatus(State.READY);

		setVisible(true);
	}

	public boolean download(String source, String dest, String quality, String format) {
		try {
			setStatus(State.READY);
			
			String videoId = source.substring(source.indexOf("?") + 1).split("=")[1];

			client = HttpClientBuilder.create().build();

			ArrayList<Pair> videoParams = getVideoParams(videoId, true/*currentTry == maxTries/*/);
			
			String fileName = getVideoTitle(videoParams);
			if (fileName == null){
				// fallback to filename = id
				fileName = videoId;
			}
			fileName += "." + format;
			
			String downloadLink = getDownloadLink(videoParams, format, quality);
			if (downloadLink == null){
				return false;
			}

			downloadFile(downloadLink, dest, fileName);
		}
		catch (Exception e) {
			e.printStackTrace();
			setStatus(State.GENERIC_ERROR, ". " + e.getMessage());
			return false;
		}
		return true;
	}

	private ArrayList<Pair> getVideoParams(String videoId, boolean useFallBackLink) {
		try {
			setStatus(State.VIDEO_PARAM_START);
			
			URI uri = null;
			
			if (useFallBackLink){
				uri = new URI("http://youtube.com/get_video_info?video_id=" + videoId);
			}
			else {
				uri = new URI("https://www.youtube.com/get_video_info?video_id=" + videoId + "&el=vevo&el=embedded&asv=3&sts=15902");
			}
				
			HttpGet request = new HttpGet();
			request.setURI(uri);
			request.setHeader("User-Agent", USER_AGENT);

			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();

			if (entity == null || response.getStatusLine().getStatusCode() != 200) {
				setStatus(State.CONNECTION_ERROR, ". Code: " + response.getStatusLine().getStatusCode());
				return null;
			}

			StringBuilder builder = new StringBuilder();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entity.getContent()));
			char buffer[] = new char[1024];
			int read = 0;
			
			while ((read = bufferedReader.read(buffer)) != -1){
				builder.append(new String(buffer, 0, read));
			}
			
			String decodedResponse = builder.toString();
			if (useFallBackLink) {
				decodedResponse = URLDecoder.decode(builder.toString(), "UTF-8");
			}
			ArrayList<Pair> encodedPairs = responseToPairs(decodedResponse, "&", "=");
			
			if (useFallBackLink){
				// Fallback, return whatever you got and pray for it to work
				return encodedPairs;
			}
			else {
				// Look for the new key value pairs
				for (Pair i : encodedPairs){
					if (i.key.equals("url_encoded_fmt_stream_map")){
						return responseToPairs(URLDecoder.decode(i.value, "UTF-8"), "&", "=");
					}
				}
			}
			
			return encodedPairs;
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		setStatus(State.VIDEO_PARAM_ERROR);
		return null;
	}

	private String getVideoTitle(ArrayList<Pair> params) {
		try {
			for (Pair i : params) {
				if (i.key.equals("title")) {
					return i.value;
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		setStatus(State.VIDEO_TITLE_ERROR);
		return null;
	}

	private String getDownloadLink(ArrayList<Pair> params, String format, String quality) {
		try {
			setStatus(State.LINK_PARSE_START);
			
			boolean getUrl = false;
			boolean checkQuality = false;

			for (Pair i : params) {
				if (i.key.equals("type") && i.value.contains(format)) {
					checkQuality = true;
					continue;
				}

				if (checkQuality && i.key.equals("quality")) {
					boolean foundQuality = i.value.equals(quality);
					checkQuality = false;
					getUrl = foundQuality;
				}

				if (getUrl && i.key.equals("url")) {
					return URLDecoder.decode(i.value, "UTF-8");
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		setStatus(State.LINK_PARSE_ERROR, ". [" + format + ";" + quality + "] not found");
		return null;
	}

	private ArrayList<Pair> responseToPairs(String input, String pairDelim, String keyValueDelim) {
		ArrayList<Pair> params = new ArrayList<Pair>();

		String kv[] = null;
		try {
			String pairs[] = input.split(pairDelim);
			for (String i : pairs) {
				kv = i.split(keyValueDelim);
				params.add(new Pair(kv[0], URLDecoder.decode(kv[1], "UTF-8")));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return params;
	}

	private void downloadFile(String downloadLink, String destination, String fileName) throws Exception{
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
		request.setHeader("User-Agent", USER_AGENT);

		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();

		if (entity == null || response.getStatusLine().getStatusCode() != 200) {
			setStatus(State.DOWNLOAD_BAD_RESPONSE, ". Code: " + response.getStatusLine().getStatusCode());
			return;
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
	}

	private void setStatus(State state){
		setStatus(state, null);
	}
	
	private void setStatus(State state, String additionalInfo){
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
	
	public static void main(String args[]) {
		new Downloader();
	}
}
