namespace cocoa TFNTwitterThriftGold
namespace java com.twitter.scrooge.test.gold.thriftjava
#@namespace scala com.twitter.scrooge.test.gold.thriftscala
#@namespace android com.twitter.scrooge.test.gold.thriftandroid

typedef i64 CollectionLongId

exception OverCapacityException {
  1: i32 chillTimeSeconds
}

exception AnotherException {
  1: i32 errorCode
}

enum RequestType {
  Create = 1 (some.annotation = "true"),
  Read = 2,
} (enum.annotation = "false")

union ResponseUnion {
  1: i64 id
  2: string details
}

struct CollectionId {
  1: required CollectionLongId collectionLongId;
}

struct Request {
  1: list<string> aList,
  2: set<i32> aSet,
  3: map<i64, i64> aMap,
  4: optional Request aRequest,
  5: list<Request> subRequests,
  6: string hasDefault = "the_default"
}

struct Response {
  1: i32 statusCode,
  2: ResponseUnion responseUnion
}

service GoldService {

  /** Hello, I'm a comment. */
  Response doGreatThings(
    1: Request request
  ) throws (
    1: OverCapacityException ex
  ) (some.annotation = "false")

} (an.annotation = "true")

service PlatinumService extends GoldService {
  i32 moreCoolThings(
    1: Request request
  ) throws (
    1: AnotherException ax,
    2: OverCapacityException oce
  )
}
