package com.company;


import java.util.ArrayList;

public class CallCentre1 {

    // the collection of 3 lines, will be initialized in main
    public static ArrayList<Line> lines;

    // number to indicate how many calls have been served. When reaching 25, end the program.
    public static volatile int finishedCallNum=0;

    //when finishedCallNum reaches 25, all calls are answered, and then call system.exit(0)
    public synchronized static int addFinishedCallNum(){
        return ++finishedCallNum;
    }

    //Implementation of my own semaphore
    public static class Sem{
        private int v;
        public Sem(int v){
            this.v=v; //volume variable
        }
        //signal method of semaphore
        public synchronized void signalS(){
            ++v;
            notify();
        }
        //wait method of semaphore
        public synchronized void waitS() throws InterruptedException {
            while(v==0){
                wait();
            }
            --v;
        }
    }

    public static class Line{
        // name of the line
        private char name;
        // the line queue
        private volatile int[] queue;
        // semaphore notFull makes sure never append to full queque
        private volatile Sem notFull;
        // sempaphore notEmpty makes sure never take call from empty queque
        private volatile Sem notEmpty;
        // sempahore setalAppendLock makes sure that steal and append to queue is mutually exclusive
        private volatile Sem stealAppendLock;
        // used in append and takeFrom end
        private volatile int in;
        // used in takeFront
        private volatile int out;

        private Line(char name){
            this.name = name;
            this.queue= new int[5];
            this.notFull=new Sem(5);
            this.notEmpty=new Sem(0);
            //the semaphore stealAppendLock is to ensure that append and take from the end will not happen at
            // the same time in case of dirty writing or reading of "in". It's a weak binary semaphore.
            this.stealAppendLock=new Sem(1);
            this.in=0;
            this.out=0;
        }

        public int numOfElments(){
            int num=0;
            for(int i=0;i<queue.length;i++){
                if(queue[i]>0){
                    num++;
                }
            }
            return num;
        }


        // Append a call to the queue.
        // the stealAppendLock prevent multiple access to the "in" variable, that is to say
        // 1. only one call can be appended to the queue at the same time
        // 2. only one worker can take from the end
        // 3. append and take from end (steal) can't happen at the same time
        public void append(Caller caller) throws InterruptedException {
            // if the line is empty, block
            notFull.waitS();
            // take stealAppendLock prevent steal process happen at the same time
            stealAppendLock.waitS();
            queue[in]=caller.name;
            in=(in+1)%5;
            Event.CallAppendedToQueue(caller.name,name);
            //release stealAppendLock lock
            stealAppendLock.signalS();
            // notify that the queue is not empty
            notEmpty.signalS();
        }



        // serve the first call of the queue
        public void takeFront(Worker worker){
            try {
                // block if the queue is empty
                notEmpty.waitS();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int call = queue[out];
            queue[out]=0;
            out=(out+1)%5;
            Event.WorkerAnswersCall(worker.name, call);
            // if finished all call, quit
            if(addFinishedCallNum()==25){
                Event.AllCallsAnswered();
                System.exit(0);
            }
            // notify that the queue is not full
            notFull.signalS();
        }

        // steal the last call
        // the stealAppendLock prevent multiple access to the "in" variable, that is to say
        // 1. only one call can be appended to the queue at the same time
        // 2. only one worker can take from the end
        // 3. append and take from end (steal) can't happen at the same time
        public void takeEnd(Worker worker){
            try {
                // block if the queue is empty
                notEmpty.waitS();
                // take the stealAppendLock to prenvent appending happen at the same time
                stealAppendLock.waitS();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            in=(in+5-1)%5;
            int call = queue[in];
            queue[in]=0;
            Event.WorkerStealsCall(worker.name, call,name);
            if(addFinishedCallNum()==25){
                Event.AllCallsAnswered();
                System.exit(0);
            }
            // release stealAppendLock
            stealAppendLock.signalS();
            // notify that the queue is not full
            notFull.signalS();
        }

    }

    public static class Worker implements Runnable{
        private int name;
        private Line line;
        private Worker(int worker, Line line){
            this.name=worker;
            this.line=line;
        }

        @Override
        public void run() {
            while(true){
                //if there is any call in the worker's own line, serve own line
                if(line.numOfElments()>0){
                    line.takeFront(this);
                }else{ // else try to find another not empty line to steal the last phone call
                    for(int i=0;i<3;i++){
                        Line line=lines.get(i);
                        if(line!=this.line&&line.numOfElments()>0){
                            line.takeEnd(this);
                            break;
                        }
                        //if all quque is empty, wait at it's own queue
                        if(i==2){
                            this.line.takeFront(this);
                        }
                    }
                }
            }
        }
    }

    private static class Caller implements Runnable{
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
            try {
                line.append(this);
            } catch (InterruptedException e) {
                e.printStackTrace();
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
