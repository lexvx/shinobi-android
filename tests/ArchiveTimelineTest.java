package ru.lex42.shinobiviewer;
import java.util.Arrays;
import java.util.List;
public final class ArchiveTimelineTest {
    static final class R implements ArchiveTimeline.Interval {
        final long s,e; R(long s,long e){this.s=s;this.e=e;}
        public long startMs(){return s;} public long endMs(){return e;}
    }
    public static void main(String[] args) {
        long epoch=1794297600000L;
        R first=new R(epoch,epoch+300000),next=new R(epoch+300000,epoch+600000),later=new R(epoch+900000,epoch+1200000);
        List<R> rows=Arrays.asList(later,next,first);
        check(ArchiveTimeline.at(rows,epoch)==first,"inclusive start");
        check(ArchiveTimeline.at(rows,epoch+300000)==next,"segment boundary");
        check(ArchiveTimeline.at(rows,epoch+700000)==null,"gap is not stale frame");
        check(ArchiveTimeline.at(rows,epoch-1)==null,"before first recording");
        check(ArchiveTimeline.at(rows,epoch+1200000)==null,"exclusive end");
        check(ArchiveTimeline.offset(next,epoch+305000)==5000,"shared clock uses absolute offset");
        check(ArchiveTimeline.clamp(epoch+2000000,epoch,epoch+1200000)==epoch+1199999,"end clamped to playable instant");
        R overlap=new R(epoch+100000,epoch+200000);
        check(ArchiveTimeline.at(Arrays.asList(first,overlap),epoch+150000)==overlap,"latest overlapping segment selected");
        long dayEnd=epoch+86400000;
        check(ArchiveTimeline.timeAt(400,1600,epoch,dayEnd)==epoch+21600000,"four-times scale shows six hours per viewport");
        check(ArchiveTimeline.timeAt(-20,1600,epoch,dayEnd)==epoch,"left coordinate clamped");
        check(ArchiveTimeline.timeAt(2000,1600,epoch,dayEnd)==dayEnd-1,"right coordinate playable clamp");
        check(ArchiveTimeline.position(epoch+3600000,1600,epoch,dayEnd)>66.66&&ArchiveTimeline.position(epoch+3600000,1600,epoch,dayEnd)<66.67,"hourly tick spacing");
        check(ArchiveTimeline.centeredScroll(800,1600,400)==600,"center selected time");
        check(ArchiveTimeline.centeredScroll(0,1600,400)==0,"scroll left clamp");
        check(ArchiveTimeline.centeredScroll(1600,1600,400)==1200,"scroll right clamp");
        check(ArchiveTimeline.timeAt(200,0,epoch,dayEnd)==epoch,"zero-width layout safe");
        check(ArchiveTimeline.timeAt(800,1600,epoch,epoch+23*3600000L)==epoch+23*1800000L,"DST day coordinate proportional");
        check(ArchiveTimeline.contentWidth(400)==1600,"explicit content width survives unspecified child measure");
        check(ArchiveTimeline.contentWidth(0)>0,"pre-layout timeline never has zero width");
        System.out.println("ArchiveTimeline: 19 tests passed");
    }
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
