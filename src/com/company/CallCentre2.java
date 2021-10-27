package com.company;


import java.util.ArrayList;
import java.util.concurrent.*;

public class CallCentre2 {

    // the collection of 3 lines, will be initialized in main
    public static ArrayList<Line> lines;

    // number to indicate how many calls have been served. When reaching 25, end the program.
    public static volatile int finishedCallNum=0;

    //when finishedCallNum reaches 25, all calls are answered, and then call system.exit(0)
    public synchronized static int addFinishedCallNum(){
        return ++finishedCallNum;
    }



    public static class Line{
        // name of the line
        private char name;
        // the line queue
        private LinkedBlockingDeque queue;

        private Line(char name){
            this.name = name;
            this.queue= new LinkedBlockingDeque<Integer>(5);
        }

        // call a line
        public boolean append(Caller caller){
            return queue.offer(caller.name);
        }

        // serve the first phone call of own line
        public Integer takeFront(Worker worker){
            //if pollFirst fails, it will return null
            Integer call= (Integer) queue.pollFirst();
            if(call!=null){
                Event.WorkerAnswersCall(worker.name, call);
                if(addFinishedCallNum()==25){
                    Event.AllCallsAnswered();
                    System.exit(0);
                }
            }
            return call;
        }

        // steal the last call
        public Integer takeEnd(Worker worker){
            //if pollLast fails, it will return null
            Integer call= (Integer) queue.pollLast();
            if(call!=null){
                Event.WorkerStealsCall(worker.name, call,this.name);
                if(addFinishedCallNum()==25){
                    Event.AllCallsAnswered();
                    System.exit(0);
                }
            }
            return call;
        }

    }

    public static class Worker implements Runnable{
        // name of the worker
        private int name;
        // worker's own line
        private Line line;
        private Worker(int worker, Line line){
            this.name=worker;
            this.line=line;
        }

        @Override
        public void run() {
            while(true){
                //try serve its own line, if fail return null
                if(line.takeFront(this)==null){
                    //if fail, steal anther line. Stealing(takeEnd) will return null if fail
                    for(int i=0;i<3;i++){
                        Line line = lines.get(i);
                        //if success, break and start all over again
                        if(line!=this.line&&line.takeEnd(this)!=null){
                            break;
                        }
                    }
                }
            }
        }
    }

    private static class Caller implements Runnable{
        // name of the caller
        private int name;
        private Caller(int name){
            this.name=name;
        }

        @Override
        public void run() {
            try {
                //sleep random time before appending to a line
                Thread.sleep(Math.round(Math.random()*100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //choose a random line to append
            int randomInt=(int) Math.round(Math.random()*2);
            Line line = lines.get(randomInt);
            while(true){
                if (line.append(this)){
                    Event.CallAppendedToQueue(this.name,line.name);
                    break;
                }else{
                    line=lines.get((++randomInt)%3);
                }
            }

        }
    }


    public static void main(String[] args) {
        //initilize three service lines
        lines = new ArrayList<>();
        lines.add(new Line('A'));
        lines.add(new Line('B'));
        lines.add(new Line('C'));
        Line worker1Line;
        Line worker2Line;
        Line worker3Line=null;
        //choose a random line for worker1
        worker1Line = lines.get((int) Math.round(Math.random()*2));
        Event.WorkerChoosesQueue(1,worker1Line.name);
        //choose a random line for worker2
        do{
            worker2Line=lines.get((int) Math.round(Math.random()*2));
        }while (worker2Line==worker1Line);
        Event.WorkerChoosesQueue(2,worker2Line.name);
        //choose a random line for worker3
        for(int i=0;i<3;i++){
            worker3Line = lines.get(i);
            if(worker3Line!=worker1Line&&worker3Line!=worker2Line){
                break;
            }
        }
        Event.WorkerChoosesQueue(3,worker3Line.name);
        //initialize 3 worker threads
        Thread workerThread1 = new Thread(new Worker(1,worker1Line));
        Thread workerThread2 = new Thread(new Worker(2,worker2Line));
        Thread workerThread3 = new Thread(new Worker(3,worker3Line));
        //start 25 caller threads
        for(int i=1;i<=25;i++){
            Thread thread = new Thread(new Caller(i));
            thread.start();
        }
        //start the 3 worker threads
        workerThread1.start();
        workerThread2.start();
        workerThread3.start();
    }
}
