package app.streamnest;
import static org.junit.Assert.*;
import java.util.*;import java.io.*;import org.junit.Test;
public class SourcePolicyTest {
 @Test(expected=IOException.class) public void rejectsWrongEpisodeEvenWithMatchingProviderFilename() throws Exception {
  EpisodeFiles.select(Arrays.asList("American.Dad.S11E08.mkv"),Arrays.asList(100L),0,"American.Dad.S11E08.mkv",true,2,1,0);
 }
 @Test(expected=IOException.class) public void rejectsIndexedUnnumberedPack() throws Exception {
  EpisodeFiles.select(Arrays.asList("a.mkv","b.mkv"),Arrays.asList(100L,200L),0,"a.mkv",true,2,1,0);
 }
 @Test public void acceptsVerifiedAbsoluteAndRejectsRange(){
  assertTrue(EpisodeFiles.matches("[Group] Anime - 108.mkv",2,6,108));
  assertFalse(EpisodeFiles.matches("Show.S01E01-E02.mkv",1,1,0));
 }
 @Test public void nativeAudioDoesNotRejectSupportedAc3(){
  Catalog.Source s=new Catalog.Source("1080p","magnet:test",1);s.release="Show AC3 English\n1080p";assertEquals(0,SourcePolicy.audioRank(s));
  s.release="Show AAC ITA Sub.ENG";assertEquals(3,SourcePolicy.audioRank(s));
  s.release="[Erai] Anime [Multiple Subtitle] [ENG][ITA]";assertEquals(1,SourcePolicy.audioRank(s));
 }
}

