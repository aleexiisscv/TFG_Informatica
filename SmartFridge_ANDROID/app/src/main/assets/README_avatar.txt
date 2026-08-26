AVATAR ANIMADO DEL ASISTENTE
============================

Coloca aqui la animacion Lottie con el nombre EXACTO:

    assistant_avatar.json

AsistenteFragment comprueba en tiempo de ejecucion si ese fichero
existe:

  - Si existe y carga bien  -> se muestra la animacion Lottie.
  - Si no existe, o el JSON esta corrupto -> se muestra el avatar
    vectorial de respaldo (res/drawable/ic_avatar_assistant.xml), que
    ademas se tine con el color primario del tema.

Es decir: la app funciona con o sin este fichero. No hace falta tocar
codigo Java para cambiar el avatar; basta con sustituir el .json.

De donde sacar la animacion:
  - lottiefiles.com (hay animaciones gratuitas con licencia abierta;
    comprueba la licencia antes de usarla en la memoria del TFG).
  - Exportando desde Adobe After Effects con el plugin Bodymovin.

Este README no interfiere: el fragmento busca el nombre concreto
"assistant_avatar.json", no cualquier fichero de la carpeta.
