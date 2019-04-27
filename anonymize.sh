#! /bin/sh

if [ -d /tmp/charlotteanon ]; then rm -Rf /tmp/charlotteanon; fi
cp -r . /tmp/charlotteanon;
rm -R /tmp/charlotteanon/target;
rm -Rf /tmp/charlotteanon/.git;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/isaacsheff/anonymousa/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/xinwenwang/anonymousb/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/Isaac/AnonymousAFirstname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/isaac/AnonymousAFirstname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/Sheff/AnonymousALastname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/sheff/AnonymousALastname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/Xinwen/AnonymousBFirstname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/xinwen/AnonymousBFirstname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/Wang/AnonymousBLastname/g' {} \;
find /tmp/charlotteanon -name '*' -exec sed -i -e 's/wang/AnonymousBLastname/g' {} \;
mv /tmp/charlotteanon/src/main/java/com/isaacsheff /tmp/charlotteanon/src/main/java/com/anonymousa;
mv /tmp/charlotteanon/src/test/java/com/isaacsheff /tmp/charlotteanon/src/test/java/com/anonymousa;
mv /tmp/charlotteanon/src/main/java/com/xinwenwang /tmp/charlotteanon/src/main/java/com/anonymousb;
mv /tmp/charlotteanon/src/test/java/com/xinwenwang /tmp/charlotteanon/src/test/java/com/anonymousb;
