package com.company;
import java.util.concurrent.atomic.AtomicMarkableReference;

//I referred to this guy's implementation (https://github.com/pramalhe/ConcurrencyFreaks/blob/master/Java/com/concurrencyfreaks/list/HarrisAMRLinkedList.java).
//But he made some same mistakes with the autor of The Art of Multi-proccessor Programing

public class LockFreeLinkedList<E>{
    final Node<E> head;
    final Node<E> tail;

    class Node<E> {
        public int key;
        public E item;
        public AtomicMarkableReference<Node<E>> next;
        public Node(E item){
            this.key = item.hashCode();
            this.item = item;
        }
    }

    public LockFreeLinkedList(){
        tail = new Node<E>(null);
        head = new Node<E>(null);
        head.next.set(tail, false);
    }

    class Window<T>{
        public Node<T> pred, curr;
        public Window(Node<T> pred, Node<T> curr){
            this.pred = pred;
            this.curr = curr;
        }
    }

    // other than returning pred and curr, find method also deletes any removed node it encounters
    public Window find (E item) {
        Node<E> pred = null;
        Node<E> curr = null;
        Node<E> succ = null;
        boolean[] marked = {false};
        retry:
        while (true) {
            pred = head;
            curr = pred.next.getReference();
            if(curr == tail){ // test if empty list
                return new Window<E>(head, tail);
            }
            while(true) {
                pred.next.get(marked);// put current node mark to marked
                succ = curr.next.getReference();
                while(!marked[0]) { // check current node mark
                    /* current node is marked, so its expected mark should be true and we link pred.next to
                    succ */
                    if(!pred.next.compareAndSet(curr,succ,true,false)){
                        //if return false means other threads changed pred.next, so we start over
                        continue retry;
                    }
                    pred.next.get(marked);
                    curr=succ;
                    if(curr == tail){ // every time curr advances, test if reaching the end
                        return new Window<E>(pred, curr);
                    }
                    succ=curr.next.getReference();
                }
                if (curr.key >= item.hashCode()) {
                    return new Window<E>(pred, curr);
                }
                pred = curr;
                curr = succ;
                if(curr == tail){ // every time curr advances, test if reaching the end
                    return new Window<E>(pred, curr);
                }
            }
        }
    }

    public boolean add(E item) {
        final Node<E> newNode = new Node<E>(item);
        while (true) {
            final Window<E> window = find(item);
            final Node<E> pred = window.pred;
            final Node<E> curr = window.curr;
            if (curr.key == item.hashCode()) {
                return false;
            } else {
                // set newNode.next to be current node
                newNode.next.set(curr, false);
                // set pred.next to be newNode, so eventually it will be like below
                // pred --->  newNode ---> curr
                if (pred.next.compareAndSet(curr, newNode, false, false)) {
                    return true;
                }
            }
        }
    }

    //remove method doesn't actually delete node, it just marks the node
    public boolean remove(E item) {
        while (true) {
            final Window<E> window = find(item);
            final Node<E> pred = window.pred;
            final Node<E> curr = window.curr;
            if (curr.key != item.hashCode()) {
                return false; // here return false means this list doesn't contain the item
            }
            //here we try to delete item simply by marking it
            return pred.next.compareAndSet(curr, curr, false, true);
        }
    }

    //contains iterate over the list and return true when finding a node with its key value same with
    //item.hashCode and its mark is false
    public boolean contains(E item) {
        boolean[] marked = {false};
        Node<E> pred = head;
        Node<E> curr = head.next.getReference();
        pred.next.get(marked);
        // jump out of the loop if the item is find or end of the list is reached.
        while (curr.hashCode()<item.hashCode() && curr != tail) {
            pred = curr;
            curr = curr.next.getReference();
            pred.next.get(marked); // notice that pred.next is the atomicMarkableReference of current node, so
            //now marked contains the mark of current node
        }
        return (curr.key == item.hashCode() && !marked[0]);
    }

}

