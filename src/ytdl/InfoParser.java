package ytdl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public class InfoParser extends JFrame{
	private enum State {
		GENERIC_ERROR("Error getting video info.."),
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

	public static final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.2.13) Gecko/20101203 Firefox/3.6.13";
	private static final long serialVersionUID = -1034923882983690258L;
	private static final int BUILD = 2;

	private CloseableHttpClient client;
	private JLabel progressLabel;
	private final AtomicBoolean isDownloading = new AtomicBoolean(false);
	private int currentTry = 1;
	private int maxTries = 3;

	public InfoParser() {
		super();
		
		client = HttpClientBuilder.create().build();
		
		setSize(400, 250);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("Youtube video downloader (build " + BUILD + ")");
		setResizable(false);
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

		add(Box.createVerticalStrut(15));
		
		JLabel urlLabel = new JLabel("Youtube video url:");
		add(urlLabel);

		final JTextField urlTextField = new JTextField();
		add(urlTextField);
		
		final JButton downloadButton = new JButton("Show info");
		downloadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (urlTextField.getText().isEmpty()){
					return;
				}
				
				if (!isDownloading.get()){
					isDownloading.set(true);
					downloadButton.setEnabled(false);
					
					new Thread(){
						public void run(){
							try {
								currentTry = 1;
								
								while (currentTry <= maxTries){
									VideoInfo info = parseInfo(urlTextField.getText());
									if (info != null)
									{
										new Downloader(client, info);
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
		
		add(Box.createVerticalStrut(15));
		
		JLabel tipLabel = new JLabel("Tip: click `" + downloadButton.getText() + "` again if there aren't enough download options.");
		add(tipLabel);
		
		add(Box.createVerticalStrut(5));
		
		add(downloadButton);

		add(Box.createVerticalStrut(5));
		
		add(new JSeparator(SwingConstants.HORIZONTAL));
		
		progressLabel = new JLabel();
		add(progressLabel);
		setStatus(State.READY);

		setVisible(true);
		pack();
	}

	public VideoInfo parseInfo(String source){
		try {
			setStatus(State.READY);

			String videoId = source.substring(source.indexOf("?") + 1).split("=")[1];
			
			ArrayList<Pair> videoParams = getVideoParams(videoId, true);
			String title = getVideoTitle(videoParams);
			
			VideoInfo videoInfo = new VideoInfo();
			
			videoInfo.title = title != null ? title : videoId;
			videoInfo.allParams = videoParams;		
			videoInfo.downloadTypes = parseDownloadTypes(videoParams);
			
			if (videoInfo.downloadTypes == null || videoInfo.downloadTypes.isEmpty()){
				return null;
			}
			
			return videoInfo;
		}
		catch (Exception e){
			e.printStackTrace();
			setStatusText(State.GENERIC_ERROR, ". " + e.getMessage());
		}
		return null;
	}

	private ArrayList<DownloadType> parseDownloadTypes(ArrayList<Pair> params){
		try {
			ArrayList<DownloadType> downloadTypes = new ArrayList<DownloadType>();
			DownloadType type = null;
			String firstParam = null;
			
			for (Pair i : params){
				if (DownloadType.isParameterNeeded(i.key)){
					System.out.printf("Arg %-30s first %-30s\n", i.key, firstParam);
					if (firstParam == null){
						firstParam = i.key;
					}
					else {
						if (type != null && type.isValid() && (firstParam.equals(i.key) || type.hasFieldSet(i.key))){
							System.out.println("Done, adding.., key is now " + i.key);
							firstParam = i.key;
							downloadTypes.add(type);
							type = new DownloadType();
						}
					}
					
					if (type == null){
						type = new DownloadType();
					}
					
					if (i.key.equals("url")){
						type.url = i.value;
					}
					else if (i.key.equals("type")){
						String typeCodec[] = i.value.split(";");
						
						if (typeCodec[0].startsWith("video")){
							type.hasVideo = true;
							
							String codecs[] = typeCodec[1].substring(typeCodec[1].indexOf("=") + 2, typeCodec[1].length() - 2).split(",");
							if (codecs.length > 1){
								type.hasAudio = true; //most likely has audio
							}
						}
						else if (typeCodec[0].startsWith("audio")){
							type.hasAudio = true;
						}
						
						type.extension = i.value.substring(i.value.indexOf("/") + 1, i.value.indexOf(";"));
						type.type = typeCodec[0];
					}
					else if (i.key.equals("quality") || i.key.equals("quality_label")){
						type.quality = i.value;
					}
					else if (i.key.equals("bitrate")){
						type.bitrate = Integer.parseInt(i.value.replaceAll("[\\D]", ""));
					}
					else {
						type.otherOptions.add(i);
					}
				}
				else {
					if (type != null){
						System.out.println(i.key + " === " + type.type + "\t" + type.quality + "\t" + type.bitrate + "\t" + (type.url != null ? "+url" : "no url"));
						if (type.isValid()){
							System.out.println("ADD");
							downloadTypes.add(type);
						}
						else {
							System.out.println("DISCARD");
							firstParam = null;
						}
						type = null;
					}
				}
			}
			
			if (type != null && type.isValid()){
				downloadTypes.add(type);
			}
			
			return downloadTypes;
		}
		catch (Exception e){
			e.printStackTrace();
			setStatusText(State.GENERIC_ERROR, ". " + e.getMessage());
		}
		
		return null;
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
			
			int timeoutInMs = 10 * 1000; // Timeout in millis.
			RequestConfig requestConfig = RequestConfig.custom()
			    .setConnectionRequestTimeout(timeoutInMs)
			    .setConnectTimeout(timeoutInMs)
			    .setSocketTimeout(timeoutInMs)
			    .build();
			
			HttpGet request = new HttpGet();
			request.setURI(uri);
			request.setHeader("User-Agent", USER_AGENT);
			request.setConfig(requestConfig);

			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();

			if (entity == null || response.getStatusLine().getStatusCode() != 200) {
				setStatusText(State.CONNECTION_ERROR, ". Code: " + response.getStatusLine().getStatusCode());
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
	
	public static void main(String args[]) {
		new InfoParser();
	}
}
