package com.company;


import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CallCentre3 {

    // the collection of 3 lines, will be initialized in main
    public static volatile ArrayList<Line> lines;

    // number to indicate how many calls have been served. When reaching 25, end the program.
    public static int finishedCallNum=0;

    public synchronized static int addFinishedCallNum(){
        //when finishedCallNum reaches 25, all calls are answered, and then call system.exit(0)
        return ++finishedCallNum;
    }

    //Implementation of my own semaphore
    public static class Monitor{
        private Lock lock;
        private Condition notEmpty;
        private Condition notFull;

        public Monitor(){
            this.lock=new ReentrantLock(true);
            this.notEmpty=lock.newCondition();
            this.notFull=lock.newCondition();
        }

        public void lock(){
            lock.lock();
        }

        public boolean tryLock(){
            return lock.tryLock();
        }

        public void unlock(){
            lock.unlock();
        }
    }

    public static class Line{
        private volatile char name;
        private volatile int[] queue;
        private Monitor monitor;
        private volatile int in;
        private volatile int out;

        private Line(char name){
            this.name = name;
            this.queue= new int[5];
            this.monitor=new Monitor();
            //the semaphore to ensure that append and take from the end will not happen at the same time in
            // case of dirty writing or reading of "in"
            //Its value is initialized as 1, appender or stealer, whoever get the lock first get the chance
            //to run.
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

        // call a line
        // the stealAppendLock prevent multiple access to the "in" variable, that is to say
        // 1. only one call can be appended to the queue at the same time
        // 2. only one worker can take from the end
        // 3. append and take from end (steal) can't happen at the same time
        public boolean append(Caller caller){
            boolean result=monitor.lock.tryLock();
            if(!result){
                return result;
            }
            try{
                while(numOfElments()==queue.length){
                    monitor.notFull.await();
                }
                queue[in]=caller.name;
                in=(in+1)%5;
                Event.CallAppendedToQueue(caller.name,name);
                monitor.notEmpty.signalAll();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                monitor.unlock();
            }
            return result;
        }

        // serve the first phone call of own line
        public boolean takeFront(Worker worker){
            boolean result=monitor.tryLock();
            if(!result){
                return result;
            }
            try{
                while(numOfElments()==0){
                    monitor.notEmpty.await();
                }
                int call = queue[out];
                queue[out]=0;
                out=(out+1)%5;
                Event.WorkerAnswersCall(worker.name, call);
                if(addFinishedCallNum()==25){
                    Event.AllCallsAnswered();
                    System.exit(0);
                }
                monitor.notFull.signal();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                monitor.lock.unlock();
            }
            return result;

        }

        // steal the last call
        // the stealAppendLock prevent multiple access to the "in" variable, that is to say
        // 1. only one call can be appended to the queue at the same time
        // 2. only one worker can take from the end
        // 3. append and take from end (steal) can't happen at the same time
        public boolean takeEnd(Worker worker){
            boolean result=monitor.tryLock();
            if(!result){
                return result;
            }
            try {
                while(numOfElments()==0){
                    monitor.notEmpty.await();
                }
                in=(in+5-1)%5;
                int call = queue[in];
                queue[in]=0;
                Event.WorkerStealsCall(worker.name, call,name);
                if(addFinishedCallNum()==25){
                    Event.AllCallsAnswered();
                    System.exit(0);
                }
                monitor.notFull.signal();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                monitor.unlock();
            }
            return result;
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
            while(true){
                if (line.append(this)){
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
