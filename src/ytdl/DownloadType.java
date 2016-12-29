package ytdl;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class DownloadType {
	public static final String POSSIBLE_PARAMS[] = {
		"url",
		"fallback_host",
		"init",
		"quality_label",
		"quality",
		"type",
		"projection_type",
		"bitrate",
		"clen",
		"itag",
		"lmt",
		"size",
		"index",
		"fps"
	};
	
	public String type = null;
	public String extension = null;
	public String quality = null;
	public String url = null;
	public int bitrate = -1;
	public boolean hasAudio = false;
	public boolean hasVideo = false;
	public ArrayList<Pair> otherOptions = new ArrayList<Pair>();
	
	public boolean isValid(){
		return (type != null || quality != null) && url != null;
	}
	
	public boolean hasFieldSet(String field){
		try {
			Field f = this.getClass().getField(field);
			Class<?> type = f.getType();
			
			System.out.println("Successfully looked up field.. " + field);
			
			if (type == String.class){
				return f.get(this) != null;
			}
			else if (type == int.class){
				return f.getInt(this) != -1;
			}
			else if (type == boolean.class){
				return f.getBoolean(this) != false;
			}
		}
		catch (Exception e){
			
		}
		
		return false;
	}
	
	public static boolean isParameterNeeded(String param){
		for (String i : POSSIBLE_PARAMS){
			if (i.equals(param)){
				return true;
			}
		}
		return false;
	}
}
