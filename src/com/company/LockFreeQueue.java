package com.company;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeQueue<E> {

    class Node<E> {
        E item;
        AtomicReference<Node> next;

        Node(E item) {
            this.item = item;
        }
    }

    AtomicReference<Node<E>> head = new AtomicReference<Node<E>>();
    AtomicReference<Node<E>> tail = head;

    public void enqueue(E item) {
        Node<E> q = new Node<E>(item);
        Node<E> p;
        boolean success = false;
        do {
            p = tail.get();
            //success == true means p.next or q is the last element of the queue
            success = p.next.compareAndSet(null, q);
            if(!success) tail.compareAndSet(p, p.next.get());
            } while (!success);
        //tail now points to p. This statement may fail as other threads may update tail before this statement
        tail.compareAndSet(p, q);
    }

    public E dequeue(){
        Node<E> result;
        boolean success = false;
        do{
            if(head==tail){ // check empty queue
                return null;
            }
            //head.next is the first node
            result = head.get().next.get();
            /*success == false means head.next != result now, that other threads already dequeued
             result */
            success = head.get().next.compareAndSet(result,result.next.get());
        }while(!success);
        return result.item;
    }

}
