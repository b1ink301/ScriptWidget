// 4. 에러 위젯: 예외가 나도 앱이 죽지 않고 에러 위젯이 표시되어야 합니다.
console.log("about to fail...");
const data = await new Request("https://httpbin.org/status/500").loadString();
console.log("httpbin returned", data.length, "bytes");
undefinedFunction(); // ReferenceError
