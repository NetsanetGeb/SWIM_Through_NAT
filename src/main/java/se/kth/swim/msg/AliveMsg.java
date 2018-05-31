package se.kth.swim.msg;

public class AliveMsg {
    int incarnationCounter;

    public AliveMsg(int incarnationCounter) {
        this.incarnationCounter = incarnationCounter;
    }

    public int getIncarnationCounter() {
        return incarnationCounter;
    }
}
