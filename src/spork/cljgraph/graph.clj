;;##Hollowed out and ported to spork.util.topograph
(ns spork.cljgraph.graph
  "A protocol and a default implementation for a Graph data type
   first we need a graph data type... 
   ANY graph is a set of edges and vertices...
   G = {{E},{V}}
   Applying a constructor on a set of edges and vertices will return
   a structure that is the representation of both edges and v's.

   Note, we want to store some notion of edge weight within the edges...

   anything that is graphable should have a few functions defined for it...
   It should have a label (or a key) that uniquely identifies it.  
   A vertex/node could be A for instance.  An edge from A to B of weight 2.0 
   could be [A -> B, 2.0]  or [A B 2.0], or {:from A :to B :weight 2.0}."
  (:use [utils.loading])
  (:require [clojure [set :as set-theory]]))
  
(declare make-graph)
  
(defn- flip 
  "Auxillary function.  Returns function that accepts args in reverse order." 
  [f & args] (apply f (reverse args)))

;nodes can contain data
;nodes do NOT contain information about other nodes....they are simply
;labeled pieces of data.  Now....said data "could" include whatever we want,
;including neighbor relations, etc. We don't do that for now....
(defprotocol ILabeled 
  (get-label [a] "Return item in label form"))

(extend-protocol ILabeled 
  java.lang.String 
  (get-label [a] a)
  java.lang.Integer
  (get-label [a] a)
  java.lang.Long  
  (get-label [a] a)
  clojre.lang.Keyword
  (get-label [a] a))

;Graph attributes are stored in meta data, so they are external to the generic
;protocol....
(defrecord graphstats [attributes counts])
(def empty-stats (graphstats. {} {})) 

(defn get-stats [g]
  (:graphstats (meta g)))

(defn alter-stats [g alterf] 
  (with-meta g 
   (assoc (meta g) :graphstats (alterf (get-stats g)))))

(defn add-attrs [g attrs]
  (alter-stats g 
     (fn [stats] 
       (let [atts (:attributes stats)]
         (assoc stats :attributes (reduce conj atts attrs))))))

(defn add-attr [g attr] (add-attrs g [attr]))
  
(defn drop-attr [g attr]
  (alter-stats g 
     (fn [stats] (merge stats {:attributes (disj (:attributes stats) attr)}))))
    
(defn drop-attrs [g attrs] 
  (reduce drop-attr g attrs))

(defn get-attrs [g] (:attributes (get-stats g)))

(defn has-attr?
  "Fetch an attribute from the graph.  Attributes describe properities of the 
   graph (i.e. directed, undirected, DAG, etc.) that inform algorithms."
  [g attr] (contains? (get-attrs g) attr))

(def digraph? (partial flip has-attr? ::digraph))
(def dag? (partial flip has-attr? ::dag))
(def ugraph? (partial flip has-attr? ::ugraph))

;  "Representation of node data.  A node is just a label (some identifier) and 
;   associated data orthogonal to topology."
(defprotocol IGraphNode
  (node-label [nd] "return the nodelabel associated with the node")
  (node-data [nd] "return any associated data in the node"))

(extend-protocol IGraphNode
  java.lang.String
    (node-label [nd] nd)
    (node-data [nd] nil)
  java.lang.Integer
    (node-label [nd] nd)
    (node-data [nd] nil)
  java.lang.Long
    (node-label [nd] nd)
    (node-data [nd] nil)
  clojure.lang.Keyword
    (node-label [nd] nd)
    (node-data [nd] nil))

(defprotocol IGraphArc
  (arc-label [a] "return any label associated with the arc")
  (arc-weight [a] "return any weight associated with the arc")
  (arc-from [a] "return the originating node of the arc")
  (arc-to [a] "return the destination node of the arc")
  (arc-nodes [a] "return a pair of [from destination]")
  (arc-data [a] "return any data associated with the arc"))

(defn litelabel [v1 v2] (str v1 "->" v2))

(def sequential-arc
  {:arc-label (fn [[label & more]] label)
   :arc-from (fn [[l from & more]] from)
   :arc-to (fn [[l f to & more]]  to)
   :arc-weight (fn [[l f t weight & more]] weight)
   :arc-nodes (fn [[l from to w & more]] [from to])
   :arc-data (fn [[l f t w d & more]] d)})

(extend-protocol IGraphArc 
  java.lang.String 
  (arc-label [a] a))
;  (arc-weight [a] )
;  (arc-from [a] )
;  (arc-to [a] )
;  (arc-nodes [a] )
;  (arc-data [a] )

(extend clojure.lang.PersistentVector
  IGraphArc sequential-arc)
(extend clojure.lang.PersistentList
  IGraphArc sequential-arc)
(extend clojure.lang.LazySeq
  IGraphArc sequential-arc)


;An IGraph is an abstraction for operating on graphs.  Note the lack of any 
;constructors for nodes or arcs....nodes and arcs are internally represented 
;by the graph - to allow for effeciencies relative to implementations - so they
;are not constructed externally.  One adds arcs (specifically, IGraphArc) to
;a graph, rather than constructing the arc explicitly.  This means we can use 
;any node/arc represenation interchangeably, although the graph will ultimately 
;use its internal representations to determine how to store graph components.
(defprotocol IGraph   
  (add-node [g n] "conjoin node n to g")
  (drop-node [g n] "disjoin node n from g")
  (has-node?  [g n] "Does g contain node n?")
  (get-node [g n] "fetch a node record from g")
  (get-nodes [g] "Return lazy-seq of nodes in g")
  (add-arc [g a]
           [g from to weight]  
           "Return new graph representing addition of arc to g.  If nodes 
            do not exist for arc (implied nodes) they are given empty, 
            default values and added.")  
  (get-arc [g a]
           [g from to] "Retreive arc a from g.") ;
  (drop-arc [g a] "Disjoin arc a from g")
  (has-arc? [g a] "Does g contain arc a?")
  (get-arcs [g] "Return lazy-seq of arcs in g")
  (get-neighbors [g n] "Return the neighborhood map for node n")
  (get-sources [g n] "Query the neighborhood to determine nodes acting as 
                      sources for this node.")
  (get-sinks [g n] "Query the neighborhood to determine nodes as sinks for n.")
  (incident-arcs [g n] "Return all arcs that use n as a component."))

;Protocol-derived functionality.  We define higher order graph operations on 
;top of protocol functions.  This allows us to change the implementation 
;to get performance benefits.   
       

(load-relative ["cljgraph.data.mapgraph"
                "cljgraph.algorithms.topsort"
                "cljgraph.io"]) 

;;default constructor for IGraphs is a mapgraph (since it's implemented first!)
(defn make-graph 
  ([] (cljgraph.data.mapgraph/make-mapgraph)))