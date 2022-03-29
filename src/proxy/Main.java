package proxy;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import http.HttpClient;
import http.HttpClient11;
import media.MovieManifest;
import media.MovieManifest.Manifest;
import media.MovieManifest.Segment;
import media.MovieManifest.SegmentContent;
import media.MovieManifest.Track;
import proxy.server.ProxyServer;

public class Main {
	static final String MEDIA_SERVER_BASE_URL = "http://localhost:9999";

	public static void main(String[] args) throws Exception {
		
		ProxyServer.start( (movie, queue) -> new DashPlaybackHandler(movie, queue) );
		
	}
	
	/**
	 * Class that implements the client-side logic.
	 * 
	 * Feeds the player queue with movie segment data fetched
	 * from the HTTP server.
	 * 
	 * The fetch algorithm should prioritise:
	 * 1) avoid stalling the browser player by allowing the queue to go empty
	 * 2) if network conditions allow, retrieve segments from higher quality tracks
	 */
	static class DashPlaybackHandler implements Runnable  {
		
		private static final int STARTING_TRACK = 0;
		private static final int FIRST_SEGMENT_IN_TRACK = 0;
		
		final String movie;
		final Manifest manifest;
		final BlockingQueue<SegmentContent> queue;
		final HttpClient http;

		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			this.http = new HttpClient11(MEDIA_SERVER_BASE_URL);
			String manifestURL = String.format("%s/%s/manifest.txt", MEDIA_SERVER_BASE_URL, movie);
            this.manifest = MovieManifest.parse(new String( http.doGet( manifestURL )));
		}
		
		/**
		 * Runs automatically in a dedicated thread.
		 * 
		 * Needs to feed the queue with segment data fast enough to
		 * avoid stalling the browser player.
		 * 
		 * Upon reaching the end of stream, the queue should
		 * be fed with a zero-length data segment.
		 */
		public void run() {
			
			int currentTrack = STARTING_TRACK;
			List<Track> tracks = manifest.tracks();
			Track track = tracks.get(currentTrack);
			Segment segment = track.segments().get(FIRST_SEGMENT_IN_TRACK);
			
			/* Sending the first segment */
			int bandwidth = downloadSegment(track, segment);
	
			/* Sending the following segments of a movie */
			for(int currentSegmentIndex = 1; currentSegmentIndex < tracks.get(STARTING_TRACK).segments().size(); currentSegmentIndex++) {
				int newTrack = bestTrackWithinQueueTime(tracks, currentTrack, bandwidth);

				/* Sending the header (first segment of a track) in case the track changes */
				if(newTrack != currentTrack) {
					track = tracks.get(newTrack);
					downloadSegment(track, track.segments().get(FIRST_SEGMENT_IN_TRACK));
				} 
				currentTrack = newTrack;
				track = tracks.get(currentTrack);
				segment = track.segments().get(currentSegmentIndex);
				bandwidth = downloadSegment(track, segment);									
			}
			
			/* Feeding the queue with a zero-length data segment upon reaching the end of the stream */
			try {
				queue.put( new SegmentContent(tracks.get(STARTING_TRACK).contentType(), new byte[0]));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
			
		}
		
		/* Auxiliary Methods */
		
		/**
		 * Gets the next best track to play the movie, 
		 * based on the current bandwidth of the network.
		 * 
		 * @param tracks      - List of tracks available
		 * @param trackIndex  - Index of the current track
		 * @param bandwidth   - Current bandwidth of the network
		 * @return trackIndex
		 */
		private int getBestTrack(List<Track> tracks, int trackIndex, int bandwidth) {
						
				while(trackIndex > 0 && bandwidth < tracks.get(trackIndex-1).avgBandwidth()) {					
					trackIndex--;
				}
				
				while(trackIndex < tracks.size() - 1 
						&& bandwidth > tracks.get(trackIndex+1).avgBandwidth()) {					
					trackIndex++;

				}	
			
			return trackIndex;
		}
		
		/**
		 * Gets the best track to play the next segment.
		 * 
		 * This choice is based not only on the evaluation of bandwidths, 
		 * but also on the available size of the queue.
		 * 
		 * @param tracks      - List of tracks available
		 * @param trackIndex  - Index of the current track
		 * @param bandwidth   - Current bandwidth of the network
		 * @return newIndex
		 */
		private int bestTrackWithinQueueTime(List<Track> tracks, int trackIndex, int bandwidth) {
				int newIndex = getBestTrack(tracks, trackIndex, bandwidth);
				if(newIndex == 0) return newIndex;
				else {
				int segmentDuration = tracks.get(trackIndex).segmentDuration();
				if(queue.size()*segmentDuration <= segmentDuration) {
					newIndex--;
				}
				}
				return newIndex;
		}
		
		/**
		 * Downloads the next segment of the movie with a given quality (in a certain track),
		 * and returns the new bandwidth of the network.
		 * 
		 * @param track           - Track of the segment
		 * @param currentSegment  - Next segment to be downloaded
		 * @return newBandwidth
		 */
		private int downloadSegment(Track track, Segment currentSegment) {
			String url = String.format("%s/%s/%s", MEDIA_SERVER_BASE_URL, movie, track.filename());

			double startTime = System.currentTimeMillis();
            byte[] segmentData = http.doGetRange(url, currentSegment.offset(), currentSegment.offset() + currentSegment.length() - 1 );
			double estimatedTime = (System.currentTimeMillis() - startTime) / Math.pow(10, 3);
			
			int x = segmentData.length*8;
			int newBandwidth = (int) (x / estimatedTime);

            try {
				queue.put( new SegmentContent(track.contentType(), segmentData));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			return newBandwidth;
		}
		
	}
}