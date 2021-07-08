# PrinterClient

## Sobre
Implementa o algoritmo de Ricart-Agrawala[1] com otimização de Roucariol-Carvalho para uma rede de computadores, comunicando através de TCP.

## Instruções de uso 
Para baixar:  
git clone -b devel https://github.com/BetoGontijo/PrinterClient/ 

Para compilar:  
javac -d bin src/main/java/br/pucminas/printerclient/*.java 

Para executar:  
java -cp ./bin br.pucminas.printerclient.PrinterClientMain  

Para depurar:  
cd /bin  
jdb  
run br.pucminas.printerclient.PrinterClientMain  

## Observações  
Cada instância do trabalho precisa executar em uma máquina diferente, recomenda-se o uso de máquinas virtuais.

## Referências
https://en.wikipedia.org/wiki/Ricart%E2%80%93Agrawala_algorithm
