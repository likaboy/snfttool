# sfntly                

## fock in `https://github.com/googlefonts/sfntly`

### 在TTF字体中提取想要的文字，让字体文件仅包含需要的文字

#### 读取一个TTF字体文件，当文件的大小小于 2M ，采用管道读取，对于2M 采用 Buffer 读取

【使用】
1. 确保你的电脑已经安装了Java环境（能运行Java命令），这是必须的。

2. 命令行进入到sfnttool所在目录下。（一个小技巧，在当前文件夹里按住Shift再右键，里面有个“在此处打开命令行”。）

3. 输入下面的命令即可：

```
java -jar sfnttool.jar  -s [textFilePath] [sourceFont.ttf] [output_file.ttf]
```

sfnttool.jar说明如下：

```
java -jar sfnttool.jar -h
subset [-?|-h|-help] [-b] [-s string] fontfile outfile
prototype font subsetter
        -?,-help        print this help information
        -s,-string       string to subset
        -b,-bench        benchmark (run 10000 iterations)
        -h,-hints        strip hints
        -w,-woff         output woff format
        -e,-eot  output eot format
        -x,-mtx  enable microtype express compression for eot format
```

4. 输出字体在同目录下。

## 配置ANT

#### 下载路径
```
https://ant.apache.org/
```

#### 配置
在电脑的环境变量中，配置当前的 ant 的程序的根路径，新增 `ANT_HOME` 
在系统变量中，然后在 path 中新增 `%ANT_HOME%/bin `

#### 调用
在 java 文件下新开一个CMD, 使用 ant 命令进行打包

#### 注意事项
项目中采用 jdk 1.8 的版本，在 javadoc.xml 的配置文件中，如果有需要，可以修改打包的jdk 版本，通常版本号根据自己电脑中的JDK 版本的变化而变化。

